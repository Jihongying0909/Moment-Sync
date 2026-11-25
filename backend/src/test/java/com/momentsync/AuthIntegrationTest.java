package com.momentsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momentsync.auth.JwtTokenService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@SpringBootTest(properties = {
        "moment.auth.secret=test-only-fixed-secret-for-auth-integration",
        "moment.auth.issuer=moment-sync-test"
})
@AutoConfigureMockMvc
@Import(AuthIntegrationTest.AdminEndpointConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class AuthIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenService tokens;
    @Autowired private JwtEncoder encoder;
    @Autowired private ObjectMapper json;
    @Autowired private BCryptPasswordEncoder passwords;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv().getOrDefault("APP_TEST_DB_URL",
                "jdbc:h2:mem:auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        if (!url.startsWith("jdbc:h2:mem:") && !url.matches("jdbc:mysql://[^/]+/moment_auth_test_[a-zA-Z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("Use a dedicated moment_auth_test_* database for integration tests");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("APP_TEST_DB_USER", "sa"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("APP_TEST_DB_PASSWORD", ""));
    }

    @BeforeAll
    void initializeDatabase() throws Exception {
        for (String file : new String[]{"schema.sql", "seed.sql"}) {
            String sql = Files.readString(Path.of("../database", file))
                    .replaceAll("(?is)CREATE DATABASE.*?;", "")
                    .replaceAll("(?im)^USE .*?;", "");
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) {
                    jdbc.execute(statement);
                }
            }
        }
    }

    @Test
    void adminQueriesOnlyAccountsAndPostsInTheReviewScope() throws Exception {
        jdbc.update("UPDATE `user` SET status = 'DISABLED' WHERE id = 2");
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = 2");
        jdbc.update("UPDATE post SET status = 'OFFLINE' WHERE id = 3");
        jdbc.update("""
                INSERT INTO post (id, user_id, title, content, visibility, status)
                VALUES (4, 3, '已删除印记', '已删除正文', 'PRIVATE', 'DELETED')
                """);
        jdbc.update("INSERT INTO post_media (post_id, media_url) VALUES (2, ?)", "https://example.test/sunset.jpg");
        String admin = "Bearer " + tokens.issue(1);

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin)
                        .param("keyword", " 小光 ").param("status", "DISABLED").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("2"))
                .andExpect(jsonPath("$.data.items[0].status").value("DISABLED"))
                .andExpect(jsonPath("$.data.items[0].length()").value(5));

        mvc.perform(get("/api/admin/posts").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        mvc.perform(get("/api/admin/posts").header(HttpHeaders.AUTHORIZATION, admin)
                        .param("visibility", "PRIVATE").param("status", "PUBLISHED").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("2"))
                .andExpect(jsonPath("$.data.items[0].visibility").value("PRIVATE"));

        mvc.perform(get("/api/admin/posts").header(HttpHeaders.AUTHORIZATION, admin).param("status", "OFFLINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("3"));

        mvc.perform(get("/api/admin/posts/2").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.content").value("运动场旁边的晚霞很漂亮。"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.test/sunset.jpg"))
                .andExpect(jsonPath("$.data.author.id").value("3"))
                .andExpect(jsonPath("$.data.tags[0].id").value("2"));

        mvc.perform(get("/api/admin/posts/3").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));

        mvc.perform(get("/api/admin/posts/4").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onlyAdministratorsCanReadPrivatePostsOrTakeThemOffline() throws Exception {
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = 2");
        String user = "Bearer " + tokens.issue(2);
        String admin = "Bearer " + tokens.issue(1);

        try {
            mvc.perform(get("/api/admin/posts/2"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40101));
            mvc.perform(get("/api/admin/posts/2").header(HttpHeaders.AUTHORIZATION, user))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40301));
            takeOffline(2, user)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40301));

            mvc.perform(get("/api/admin/posts/2").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
            takeOffline(2, admin)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("OFFLINE"));
            mvc.perform(get("/api/admin/posts/2").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                    .andExpect(jsonPath("$.data.status").value("OFFLINE"));
        } finally {
            jdbc.update("UPDATE post SET visibility = 'PUBLIC', status = 'PUBLISHED' WHERE id = 2");
        }
    }

    @Test
    void takingAPostOfflineIsRepeatableAndBlocksPublicSearchAndComments() throws Exception {
        String admin = "Bearer " + tokens.issue(1);
        String user = "Bearer " + tokens.issue(2);
        mvc.perform(get("/api/posts/search").param("keyword", "图书馆").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("1"));
        mvc.perform(get("/api/posts/1/comments").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(patch("/api/admin/posts/1/status").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OFFLINE\"}{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(get("/api/admin/posts/1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        takeOffline(1, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));
        takeOffline(1, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));
        mvc.perform(patch("/api/admin/posts/1/status").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mvc.perform(get("/api/admin/posts/1").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));

        mvc.perform(get("/api/posts/search").param("keyword", "图书馆").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/posts/1/comments").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        mvc.perform(post("/api/posts/1/comments").header(HttpHeaders.AUTHORIZATION, user)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"新的留言\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void adminStatisticsCountTheReviewScopeAndStayStableAfterTakeDown() throws Exception {
        jdbc.update("UPDATE `user` SET status = 'DISABLED' WHERE id = 2");
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = 2");
        jdbc.update("UPDATE post SET status = 'OFFLINE' WHERE id = 3");
        jdbc.update("""
                INSERT INTO post (id, user_id, title, content, status)
                VALUES (4, 3, '已删除印记', '已删除正文', 'DELETED')
                """);
        jdbc.update("""
                INSERT INTO comment (post_id, user_id, content, status) VALUES
                (1, 3, '已删除评论', 'DELETED'),
                (3, 3, '下架印记的评论', 'PUBLISHED'),
                (4, 3, '已删除印记的评论', 'PUBLISHED')
                """);
        String admin = "Bearer " + tokens.issue(1);

        expectAdminStatistics(admin);
        takeOffline(1, admin).andExpect(status().isOk());
        expectAdminStatistics(admin);
    }

    private void expectAdminStatistics(String admin) throws Exception {
        mvc.perform(get("/api/admin/statistics").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userCount").value(2))
                .andExpect(jsonPath("$.data.postCount").value(3))
                .andExpect(jsonPath("$.data.commentCount").value(3));
    }

    private ResultActions takeOffline(long id, String bearer) throws Exception {
        return mvc.perform(patch("/api/admin/posts/{id}/status", id).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OFFLINE\"}"));
    }

    @Test
    void healthChecksTheDatabaseWithoutRequiringLogin() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.database").value("UP"));
    }

    @Test
    void anonymousRequestsReceiveTheShared401Response() throws Exception {
        mvc.perform(post("/api/posts/1/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"未登录评论"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void verifiedAccountCanSearchAndPublishesAsTheSignedInAuthor() throws Exception {
        String token = "Bearer " + tokens.issue(2);
        mvc.perform(get("/api/posts/search").param("keyword", "图书馆").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("1"));

        mvc.perform(post("/api/posts/1/comments").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"content":"后端联调评论"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.id").value("2"));

        Long author = jdbc.queryForObject("SELECT user_id FROM comment WHERE content = ?", Long.class, "后端联调评论");
        assertThat(author).isEqualTo(2L);
    }

    @Test
    void currentDatabaseRoleControlsAdminAccess() throws Exception {
        mvc.perform(get("/api/admin/test-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        String adminToken = "Bearer " + tokens.issue(1);
        mvc.perform(get("/api/admin/test-only").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        jdbc.update("UPDATE `user` SET role = 'USER' WHERE id = 1");
        mvc.perform(get("/api/admin/test-only").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledAccountLosesAccessImmediately() throws Exception {
        String token = "Bearer " + tokens.issue(2);
        jdbc.update("UPDATE `user` SET status = 'DISABLED' WHERE id = 2");
        mvc.perform(get("/api/posts/search").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void expiredAndMalformedTokensReceiveTheShared401Response() throws Exception {
        JwtClaimsSet expired = JwtClaimsSet.builder().issuer("moment-sync-test").subject("2")
                .issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(1)).build();
        String expiredToken = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), expired)).getTokenValue();

        for (String token : new String[]{expiredToken, "invalid"}) {
            mvc.perform(get("/api/posts/search").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40101));
        }
    }

    @Test
    void configuredAdminWebOriginCanSendAnAuthorizationHeader() throws Exception {
        mvc.perform(options("/api/admin/users")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void passwordLoginReturnsTokenAndProfileForTheMiniProgram() throws Exception {
        setPassword("student_a", " a-demo-password ");
        String reply = login("/api/auth/login", " student_a ", " a-demo-password ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.id").value("2"))
                .andExpect(jsonPath("$.data.user.username").value("student_a"))
                .andExpect(jsonPath("$.data.user.nickname").value("小光"))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String token = json.readTree(reply).path("data").path("token").asText();
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("2"))
                .andExpect(jsonPath("$.data.role").value("USER"));

        mvc.perform(post("/api/posts/1/comments").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"content":"账号密码登录后的评论"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author.id").value("2"));
    }

    @Test
    void adminLoginRequiresAnAdminAfterCheckingThePassword() throws Exception {
        setPassword("student_a", "student-password");
        setPassword("admin_demo", "admin-password");
        login("/api/admin/auth/login", "student_a", "incorrect")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        login("/api/admin/auth/login", "student_a", "student-password")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        String reply = login("/api/admin/auth/login", "admin_demo", "admin-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(reply).path("data").path("token").asText();
        mvc.perform(get("/api/admin/test-only").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordUnknownAndDisabledAccountsReturnTheSameError() throws Exception {
        setPassword("student_a", "student-password");
        String badPassword = login("/api/auth/login", "student_a", "incorrect")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login("/api/auth/login", "missing-user", "student-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        jdbc.update("UPDATE `user` SET status = 'DISABLED' WHERE username = 'student_a'");
        String disabledUser = login("/api/auth/login", "student_a", "student-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(unknownUser).isEqualTo(badPassword);
        assertThat(disabledUser).isEqualTo(badPassword);
    }

    @Test
    void invalidLoginPayloadsUseTheShared400Response() throws Exception {
        for (String body : new String[]{"{", "{}", "{\"username\":\"student_a\",\"password\":\" \"}"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }
        login("/api/auth/login", "student_a", "长".repeat(25))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void bothLoginEndpointsRejectNonStringCredentialsAndUnsupportedMediaTypes() throws Exception {
        for (String path : new String[]{"/api/auth/login", "/api/admin/auth/login"}) {
            for (String body : new String[]{
                    json.writeValueAsString(Map.of("username", "student_a", "password", 12345678)),
                    json.writeValueAsString(Map.of("username", true, "password", "demo-password"))
            }) {
                mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(40001));
            }

            mvc.perform(post(path).contentType(MediaType.TEXT_PLAIN).content("""
                            {"username":"student_a","password":"demo-password"}
                            """))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value(41501))
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    @Test
    void loginAcceptsCorrectCredentialsWhenTheClientStillHasAnInvalidToken() throws Exception {
        setPassword("student_a", "student-password");
        mvc.perform(post("/api/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer stale-token")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"username":"student_a","password":"student-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value("2"));
    }

    @Test
    void currentUserRequiresAValidSession() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        String token = tokens.issue(2);
        jdbc.update("UPDATE `user` SET status = 'DISABLED' WHERE username = 'student_a'");
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    private ResultActions login(String path, String username, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("username", username, "password", password));
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void setPassword(String username, String password) {
        jdbc.update("UPDATE `user` SET password_hash = ? WHERE username = ?", passwords.encode(password), username);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AdminEndpointConfiguration {
        @Bean
        RouterFunction<ServerResponse> testAdminEndpoint() {
            return RouterFunctions.route().GET("/api/admin/test-only", request -> ServerResponse.ok().build()).build();
        }
    }
}
