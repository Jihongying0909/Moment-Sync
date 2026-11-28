package com.momentsync.interaction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import com.momentsync.MomentSyncApplication;
import com.momentsync.auth.CurrentUserProvider;
import com.momentsync.moderation.ContentPolicy;
import com.momentsync.web.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = MomentSyncApplication.class,
        properties = "moment.auth.secret=test-only-fixed-secret-for-interaction-integration")
@Import(InteractionIntegrationTest.TestIdentity.class)
@AutoConfigureMockMvc(addFilters = false)
class InteractionIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class TestIdentity {
        // 只存在于测试 classpath；生产模块没有测试账号、伪 JWT 或鉴权后门。
        @Bean @Primary CurrentUserProvider testUsers() {
            return () -> {
                var request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
                String token = request.getHeader("Authorization");
                if ("Bearer test-user-2".equals(token)) return 2L;
                if ("Bearer test-user-3".equals(token)) return 3L;
                throw new ApiException(401, 40101, "请先登录");
            };
        }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv().getOrDefault("INTERACTION_TEST_DB_URL", "jdbc:h2:mem:interaction;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        if (!url.startsWith("jdbc:h2:mem:") && !url.matches("jdbc:mysql://[^/]+/interaction_test_[a-zA-Z0-9_]+(?:\\?.*)?"))
            throw new IllegalArgumentException("集成测试只允许专用 interaction_test_* 数据库");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("INTERACTION_TEST_DB_USER", "sa"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("INTERACTION_TEST_DB_PASSWORD", ""));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ContentPolicy policy;
    @BeforeEach void initialize() throws Exception {
        String schema = Files.readString(Path.of("../database/schema.sql"));
        // 复用仓库真实表结构，仅剔除建库/USE，防止写入团队数据库。
        schema = schema.replaceAll("(?is)CREATE DATABASE.*?;", "").replaceAll("(?im)^USE .*?;", "");
        for (String statement : schema.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        for (String table : new String[]{"comment", "post_tag", "post_media", "post", "tag", "`user`"}) jdbc.update("DELETE FROM " + table);
        String seed = Files.readString(Path.of("../database/seed.sql")).replaceAll("(?im)^USE .*?;", "");
        for (String statement : seed.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
    }
    @Test void searchTitleAndBody() throws Exception {
        search("图书馆", null).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value("1"));
        search("需求整理", null).andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void tagAndKeywordAreCombinedWithoutDuplicates() throws Exception {
        search("晚霞", "2").andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].tags.length()").value(2));
        search("晚霞", "1").andExpect(jsonPath("$.data.total").value(0));
        search("", "999").andExpect(jsonPath("$.data.total").value(0));
        jdbc.update("UPDATE tag SET status='DISABLED' WHERE id=2");
        search("", "2").andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void onlyPublicPublishedPostsCanBeReadOrCommented() throws Exception {
        jdbc.update("UPDATE post SET status='OFFLINE' WHERE id=1");
        jdbc.update("UPDATE post SET visibility='PRIVATE' WHERE id=2");
        search("", null).andExpect(jsonPath("$.data.total").value(1));
        for (long id : new long[]{1, 2, 999}) {
            mvc.perform(get("/api/posts/" + id + "/comments").header("Authorization", "Bearer test-user-2")).andExpect(status().isNotFound());
            publish(id, "留言").andExpect(status().isNotFound());
        }
    }
    @Test void sqlWildcardsAndInjectionAreLiteral() throws Exception {
        jdbc.update("UPDATE post SET title='完成 100%_! 进度' WHERE id=1");
        search("%_!", null).andExpect(jsonPath("$.data.total").value(1));
        search("' OR 1=1 --", null).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void stablePaginationAndEmptyPage() throws Exception {
        jdbc.update("UPDATE post SET created_at='2026-11-01 12:00:00'");
        mvc.perform(get("/api/posts/search").param("pageSize", "1").param("page", "2").header("Authorization", "Bearer test-user-2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.items[0].id").value("2"));
        mvc.perform(get("/api/posts/search").param("page", "100").header("Authorization", "Bearer test-user-2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }
    @Test void requiresLoginOnEveryEndpoint() throws Exception {
        mvc.perform(get("/api/posts/search")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/posts/1/comments")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/posts/1/comments").contentType("application/json").content("{\"content\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void invalidQueryReturnsUnifiedErrors() throws Exception {
        for (var entry : Map.of("page", "0", "pageSize", "51", "tagId", "-1", "keyword", "x".repeat(101)).entrySet()) {
            mvc.perform(get("/api/posts/search").param(entry.getKey(), entry.getValue()).header("Authorization", "Bearer test-user-2"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        }
        mvc.perform(get("/api/posts/search").param("tagId", "abc").header("Authorization", "Bearer test-user-2"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
    }
    @Test void publishedCommentPersistsAndUsesAuthenticatedAuthor() throws Exception {
        publish(1, "  一起加油！  ").andExpect(status().isOk()).andExpect(jsonPath("$.data.author.id").value("2"))
                .andExpect(jsonPath("$.data.content").value("一起加油！"));
        mvc.perform(get("/api/posts/1/comments").header("Authorization", "Bearer test-user-3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].content").value("一起加油！"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment WHERE content='一起加油！' AND user_id=2", Long.class)).isEqualTo(1L);
        search("图书馆", null).andExpect(jsonPath("$.data.items[0].commentCount").value(2));
    }
    @Test void callerCannotSpoofCommentAuthor() throws Exception {
        mvc.perform(post("/api/posts/1/comments").header("Authorization", "Bearer test-user-2").contentType("application/json")
                .content("{\"content\":\"我的留言\",\"userId\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.author.id").value("2"));
    }
    @Test void rejectsEmptyOversizeOrMalformedComments() throws Exception {
        for (String content : new String[]{"", "   ", "\u200b\u3000", "x".repeat(501)}) publish(1, content).andExpect(status().isBadRequest());
        mvc.perform(post("/api/posts/1/comments").header("Authorization", "Bearer test-user-2").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/posts/1/comments").header("Authorization", "Bearer test-user-2").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
        publish(1, "x".repeat(500)).andExpect(status().isOk());
    }
    @Test void sensitiveContentIsBlockedBeforeInsert() throws Exception {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM comment", Long.class);
        for (String content : new String[]{"敏感词测试", "敏 感词\u200b测试", "请代开发票"}) {
            publish(1, content).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(42201));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comment", Long.class)).isEqualTo(before);
        assertThatThrownBy(() -> policy.check("办假证")).isInstanceOf(ApiException.class);
    }
    @Test void disabledUserCannotPublish() throws Exception {
        jdbc.update("UPDATE `user` SET status='DISABLED' WHERE id=2");
        publish(1, "hello").andExpect(status().isUnauthorized());
    }
    @Test void unpublishedCommentsAreExcludedAndListIsPaginated() throws Exception {
        jdbc.update("UPDATE comment SET status='DELETED' WHERE post_id=1");
        mvc.perform(get("/api/posts/1/comments").header("Authorization", "Bearer test-user-2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        publish(1, "第一条"); publish(1, "第二条");
        mvc.perform(get("/api/posts/1/comments").param("pageSize", "1").param("page", "2").header("Authorization", "Bearer test-user-2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.items[0].content").value("第一条"));
    }
    @Test void bigintIdsAreSerializedAsStrings() throws Exception {
        jdbc.update("INSERT INTO post(id,user_id,title,content) VALUES (9007199254740993,2,'大 ID','内容')");
        search("大 ID", null).andExpect(jsonPath("$.data.items[0].id").value("9007199254740993"));
    }
    private org.springframework.test.web.servlet.ResultActions search(String keyword, String tag) throws Exception {
        var request = get("/api/posts/search").param("keyword", keyword).header("Authorization", "Bearer test-user-2");
        if (tag != null) request.param("tagId", tag);
        return mvc.perform(request).andExpect(status().isOk());
    }
    private org.springframework.test.web.servlet.ResultActions publish(long id, String content) throws Exception {
        String body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("content", content));
        return mvc.perform(post("/api/posts/" + id + "/comments").header("Authorization", "Bearer test-user-2")
                .contentType("application/json").content(body));
    }
}
