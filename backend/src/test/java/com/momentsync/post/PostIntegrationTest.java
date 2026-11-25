package com.momentsync.post;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.momentsync.auth.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "moment.auth.secret=test-only-fixed-secret-for-post-integration")
@AutoConfigureMockMvc
class PostIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtTokenService tokens;
    @Autowired private PasswordEncoder passwords;
    private String token;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv().getOrDefault("POST_TEST_DB_URL",
                "jdbc:h2:mem:posts;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        if (!url.startsWith("jdbc:h2:mem:")
                && !url.matches("jdbc:mysql://[^/]+/moment_post_test_[a-zA-Z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("集成测试只允许专用 moment_post_test_* 数据库");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("POST_TEST_DB_USER", "sa"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("POST_TEST_DB_PASSWORD", ""));
    }

    @BeforeEach
    void initialize() throws Exception {
        String schema = Files.readString(Path.of("../database/schema.sql"))
                .replaceAll("(?is)CREATE DATABASE.*?;", "").replaceAll("(?im)^USE .*?;", "");
        executeScript(schema);
        for (String table : List.of("comment", "post_tag", "post_media", "post", "tag", "`user`")) {
            jdbc.update("DELETE FROM " + table);
        }
        executeScript(Files.readString(Path.of("../database/seed.sql")).replaceAll("(?im)^USE .*?;", ""));
        token = tokens.issue(2L);
    }

    @Test
    void publishesCompletePostAndReadsSameDataThroughHomeDetailAndSearch() throws Exception {
        var body = draft().put("imageUrl", "/uploads/sunset.jpg").put("mood", "开心")
                .put("campusLocation", "运动场");
        body.putArray("tagIds").add("2").add("5").add("2");
        JsonNode created = data(publish(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.id").value("2"))
                .andExpect(jsonPath("$.data.author.nickname").value("小光"))
                .andExpect(jsonPath("$.data.imageUrl").value("/uploads/sunset.jpg"))
                .andExpect(jsonPath("$.data.tags.length()").value(2))
                .andExpect(jsonPath("$.data.commentCount").value(0)));
        assertThat(created.get("id").isTextual()).isTrue();
        assertThat(created.get("createdAt").asText()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        String id = created.get("id").asText();
        assertThat(data(detail(id))).isEqualTo(created);
        assertThat(data(mvc.perform(get("/api/posts").header("Authorization", bearer())))
                .get("items").get(0)).isEqualTo(created);
        assertThat(data(mvc.perform(get("/api/posts/search").param("keyword", "我的新印记")
                .header("Authorization", bearer()))).get("items").get(0)).isEqualTo(created);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_media WHERE post_id=?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_tag WHERE post_id=?", Long.class, id)).isEqualTo(2);
    }

    @Test
    void acceptsTextOnlyPostAndUsesTrustedAuthorAndVisibility() throws Exception {
        var body = draft().put("userId", 3).put("status", "OFFLINE").put("visibility", "PRIVATE");
        var created = data(publish(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.id").value("2"))
                .andExpect(jsonPath("$.data.tags").isEmpty()));
        assertThat(created.get("imageUrl").isNull()).isTrue();
        assertThat(created.get("mood").isNull()).isTrue();
        assertThat(created.get("campusLocation").isNull()).isTrue();
        String id = created.get("id").asText();
        assertThat(jdbc.queryForObject("SELECT visibility FROM post WHERE id=?", String.class, id)).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("SELECT status FROM post WHERE id=?", String.class, id)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_media WHERE post_id=?", Long.class, id)).isZero();
        detail(id).andExpect(status().isOk());
    }

    @Test
    void preservesBigintIdsWithoutJavascriptRounding() throws Exception {
        jdbc.update("INSERT INTO tag(id,name) VALUES (9007199254740993,'大标签')");
        var body = draft();
        body.putArray("tagIds").add("9007199254740993");
        publish(body).andExpect(status().isOk()).andExpect(jsonPath("$.data.tags[0].id").value("9007199254740993"));
        jdbc.update("INSERT INTO post(id,user_id,title,content) VALUES (9007199254740993,2,'大 ID','内容')");
        detail("9007199254740993").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("9007199254740993"));
    }

    @Test
    void paginatesStablyAndKeepsTotalOnEmptyPages() throws Exception {
        jdbc.update("UPDATE post SET created_at='2026-11-01 12:00:00'");
        mvc.perform(get("/api/posts").param("page", "2").param("pageSize", "1").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value("2"))
                .andExpect(jsonPath("$.data.page").value(2)).andExpect(jsonPath("$.data.pageSize").value(1));
        mvc.perform(get("/api/posts").param("page", "100").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OFFLINE", "DELETED", "PRIVATE"})
    void unavailablePostsDisappearFromAllPublicEntrypoints(String visibility) throws Exception {
        if ("PRIVATE".equals(visibility)) {
            jdbc.update("UPDATE post SET visibility='PRIVATE' WHERE id=1");
        } else {
            jdbc.update("UPDATE post SET status=? WHERE id=1", visibility);
        }
        detail("1").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40401));
        mvc.perform(get("/api/posts").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/posts/search").param("keyword", "图书馆").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/posts/1/comments").header("Authorization", bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/posts/1/comments").header("Authorization", bearer())
                        .contentType("application/json").content("{\"content\":\"留言\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void detailReflectsCommentCountAndExcludesDisabledTags() throws Exception {
        mvc.perform(post("/api/posts/1/comments").header("Authorization", bearer())
                        .contentType("application/json").content("{\"content\":\"联调留言\"}"))
                .andExpect(status().isOk());
        detail("1").andExpect(jsonPath("$.data.commentCount").value(2));
        jdbc.update("UPDATE comment SET status='DELETED' WHERE id=1");
        jdbc.update("UPDATE tag SET status='DISABLED' WHERE id=1");
        detail("1").andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.tags").isEmpty());
    }

    @Test
    void rejectsUnknownOrDisabledTagsBeforeWriting() throws Exception {
        jdbc.update("UPDATE tag SET status='DISABLED' WHERE id=1");
        for (String id : List.of("1", "99999")) {
            var body = draft();
            body.putArray("tagIds").add(id);
            publish(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        }
        assertThat(rowCounts()).containsExactly(3L, 0L, 4L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "content"})
    void moderatesBothTitleAndBodyWithoutPartialWrites(String field) throws Exception {
        publish(draft().put(field, "敏 感词\u200b测试"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(42201));
        assertThat(rowCounts()).containsExactly(3L, 0L, 4L);
    }

    @Test
    void rollsBackPostAndImageWhenTagInsertFails() throws Exception {
        // 在最后一步制造真实数据库约束失败，验证前两步写入一并回滚。
        jdbc.execute("ALTER TABLE post_tag ADD CONSTRAINT reject_test_tag CHECK (post_id <= 3 OR tag_id <> 5)");
        try {
            var body = draft().put("imageUrl", "/uploads/rollback.jpg");
            body.putArray("tagIds").add("2").add("5");
            publish(body).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(50001));
            assertThat(rowCounts()).containsExactly(3L, 0L, 4L);
        } finally {
            try (var connection = jdbc.getDataSource().getConnection()) {
                String drop = "MySQL".equals(connection.getMetaData().getDatabaseProductName()) ? "DROP CHECK" : "DROP CONSTRAINT";
                jdbc.execute("ALTER TABLE post_tag " + drop + " reject_test_tag");
            }
        }
    }

    @Test
    void authenticatesThroughExistingPasswordLoginBeforePublishing() throws Exception {
        jdbc.update("UPDATE `user` SET password_hash=? WHERE id=2", passwords.encode("post-test-password"));
        token = data(mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"student_a\",\"password\":\"post-test-password\"}"))
                .andExpect(status().isOk())).get("token").asText();
        String id = data(publish(draft()).andExpect(status().isOk())).get("id").asText();
        detail(id).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.id").value("2"));
    }

    @Test
    void requiresValidJwtAndRejectsDisabledAccounts() throws Exception {
        mvc.perform(get("/api/posts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/posts/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/posts").contentType("application/json").content(draft().toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/posts").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40101));
        jdbc.update("UPDATE `user` SET status='DISABLED' WHERE id=2");
        publish(draft()).andExpect(status().isUnauthorized());
        detail("1").andExpect(status().isUnauthorized());
        assertThat(rowCounts()).containsExactly(3L, 0L, 4L);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsMalformedPayloadsWithUnifiedError(String body) throws Exception {
        mvc.perform(post("/api/posts").header("Authorization", bearer()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        assertThat(rowCounts()).containsExactly(3L, 0L, 4L);
    }

    static Stream<String> invalidBodies() {
        return Stream.of("{", "[]", "null", "{}", "{\"title\":1,\"content\":\"正文\"}",
                "{\"title\":\"标题\",\"content\":false}", "{\"title\":\" \u200b\",\"content\":\"正文\"}",
                "{\"title\":\"标题\",\"content\":\"正文\",\"tagIds\":[2]}",
                "{\"title\":\"标题\",\"content\":\"正文\",\"tagIds\":[\"9223372036854775808\"]}",
                "{\"title\":\"标题\",\"content\":\"正文\",\"tagIds\":[\"-1\"]}",
                "{\"title\":\"标题\",\"content\":\"正文\",\"tagIds\":{}}",
                "{\"title\":\"标题\",\"content\":\"正文\",\"imageUrl\":true}");
    }

    @Test
    void enforcesFieldLimitsAtApiBoundary() throws Exception {
        for (var field : Map.of("title", 101, "content", 2001, "imageUrl", 501, "mood", 31, "campusLocation", 101).entrySet()) {
            publish(draft().put(field.getKey(), "x".repeat(field.getValue())))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        }
        var tooManyTags = draft();
        tooManyTags.putArray("tagIds").add("1").add("2").add("3").add("4").add("5").add("6");
        publish(tooManyTags).andExpect(status().isBadRequest());
        publish(draft().put("title", "x".repeat(100)).put("content", "x".repeat(2000)))
                .andExpect(status().isOk());
    }

    @Test
    void invalidPathsPaginationAndMediaTypeUseExpectedCodes() throws Exception {
        for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
            detail(id).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        }
        detail("99999").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40401));
        for (var param : Map.of("page", "100001", "pageSize", "51").entrySet()) {
            mvc.perform(get("/api/posts").param(param.getKey(), param.getValue()).header("Authorization", bearer()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        }
        mvc.perform(post("/api/posts").header("Authorization", bearer()).contentType("text/plain").content("text"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(41501));
    }

    private ObjectNode draft() {
        return json.createObjectNode().put("title", "我的新印记").put("content", "今天完成了发布与查询联调。");
    }

    private ResultActions publish(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/posts").header("Authorization", bearer()).contentType("application/json")
                .content(body.toString()));
    }

    private ResultActions detail(String id) throws Exception {
        return mvc.perform(get("/api/posts/" + id).header("Authorization", bearer()));
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private JsonNode data(ResultActions response) throws Exception {
        return json.readTree(response.andReturn().getResponse().getContentAsByteArray()).get("data");
    }

    private List<Long> rowCounts() {
        return List.of(jdbc.queryForObject("SELECT COUNT(*) FROM post", Long.class),
                jdbc.queryForObject("SELECT COUNT(*) FROM post_media", Long.class),
                jdbc.queryForObject("SELECT COUNT(*) FROM post_tag", Long.class));
    }

    private void executeScript(String sql) {
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement);
            }
        }
    }
}
