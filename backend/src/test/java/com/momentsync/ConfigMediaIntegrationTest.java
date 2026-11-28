package com.momentsync;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momentsync.auth.JwtTokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "moment.auth.secret=test-only-config-media-secret-at-least-32-bytes",
    "spring.datasource.url=jdbc:h2:mem:configmedia;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "moment.media.public-base-url=https://images.example.test",
    "moment.campus.moods=开心,平静,开心", "moment.campus.locations=图书馆,运动场"
})
@AutoConfigureMockMvc
class ConfigMediaIntegrationTest {
    static final Path UPLOADS;
    static {
        try { UPLOADS = Files.createTempDirectory("moment-images-test-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("moment.media.directory", UPLOADS::toString);
        String url = System.getenv("CONFIG_MEDIA_TEST_DB_URL");
        if (url != null) {
            if (!url.matches("jdbc:mysql://[^/]+/moment_media_test_[a-zA-Z0-9_]+(?:\\?.*)?"))
                throw new IllegalArgumentException("Use a dedicated moment_media_test_* database");
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("CONFIG_MEDIA_TEST_DB_USER", "root"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("CONFIG_MEDIA_TEST_DB_PASSWORD", ""));
        }
    }
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired ObjectMapper json;

    @BeforeEach void initialize() throws Exception {
        String schema = Files.readString(Path.of("../database/schema.sql"))
                .replaceAll("(?is)CREATE DATABASE.*?;", "").replaceAll("(?im)^USE .*?;", "");
        for (String statement : schema.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        for (String table : new String[]{"comment", "post_tag", "post_media", "post", "tag", "`user`"})
            jdbc.update("DELETE FROM " + table);
        String seed = Files.readString(Path.of("../database/seed.sql")).replaceAll("(?im)^USE .*?;", "");
        for (String statement : seed.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
    }
    @AfterAll static void cleanup() throws Exception {
        try (var files = Files.list(UPLOADS)) {
            for (Path file : files.toList()) Files.delete(file);
        }
        Files.delete(UPLOADS);
    }
    String auth() { return "Bearer " + tokens.issue(2); }
    byte[] image(String format) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }
    @Test void allConfigurationAndUploadRequireRealJwt() throws Exception {
        for (String path : new String[]{"/api/tags", "/api/moods", "/api/locations"})
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40101));
        mvc.perform(multipart("/api/files/images").file("file", image("png")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tags").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
    @Test void configurationUsesActiveDatabaseTagsAndConfiguredNames() throws Exception {
        jdbc.update("UPDATE tag SET status='DISABLED' WHERE id=1");
        jdbc.update("INSERT INTO tag(id,name) VALUES (9007199254740993,'大整数标签')");
        mvc.perform(get("/api/tags").header("Authorization", auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].id").value("2"))
                .andExpect(jsonPath("$.data[4].id").value("9007199254740993"));
        mvc.perform(get("/api/moods").header("Authorization", auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0]").value("开心"));
        mvc.perform(get("/api/locations").header("Authorization", auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[1]").value("运动场"));
    }
    @Test void uploadRoundTripsBothFormatsWithoutTrustingNameOrMime() throws Exception {
        for (String format : new String[]{"png", "jpeg"}) {
            byte[] original = image(format);
            byte[] withTrailer = java.util.Arrays.copyOf(original, original.length + 15);
            System.arraycopy("<script>".getBytes(), 0, withTrailer, original.length, 8);
            String body = mvc.perform(multipart("/api/files/images")
                    .file(new MockMultipartFile("file", "../../evil.html", "text/html", withTrailer))
                    .header("Authorization", auth())).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String url = json.readTree(body).path("data").path("imageUrl").asText();
            assertThat(url).startsWith("https://images.example.test/api/files/images/");
            String path = java.net.URI.create(url).getPath();
            byte[] downloaded = mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", format.equals("png") ? "image/png" : "image/jpeg"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(ImageIO.read(new java.io.ByteArrayInputStream(downloaded)).getWidth()).isEqualTo(4);
            assertThat(new String(downloaded, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("<script>");
        }
    }
    @Test void rejectsMissingMultipleEmptyFakeAndUnsupportedFiles() throws Exception {
        mvc.perform(multipart("/api/files/images").header("Authorization", auth()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        mvc.perform(multipart("/api/files/images").file("wrong", image("png")).header("Authorization", auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/files/images").file("file", image("png")).file("file", image("png"))
                .header("Authorization", auth())).andExpect(status().isBadRequest());
        for (byte[] bytes : new byte[][]{new byte[0], "fake image".getBytes(), image("gif")})
            mvc.perform(multipart("/api/files/images").file(new MockMultipartFile("file", "fake.png", "image/png", bytes))
                    .header("Authorization", auth())).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
    }
    @Test void rejectsOversizeAndLargePixelImagesBeforeDecode() throws Exception {
        mvc.perform(multipart("/api/files/images").file("file", new byte[10 * 1024 * 1024 + 1])
                .header("Authorization", auth())).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(41301));
        byte[] png = image("png");
        java.nio.ByteBuffer.wrap(png, 16, 8).putInt(50000).putInt(50000);
        mvc.perform(multipart("/api/files/images").file("file", png).header("Authorization", auth()))
                .andExpect(status().isBadRequest());
    }
    @Test void noAnonymousDirectoryListingAndMissingImageHasUnifiedError() throws Exception {
        mvc.perform(get("/api/files/images")).andExpect(status().isUnauthorized());
        for (String name : new String[]{"application.yml", "00000000-0000-0000-0000-000000000000.png"})
            mvc.perform(get("/api/files/images/" + name)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(40401));
        mvc.perform(post("/api/files/images").contentType("application/json").content("{}")
                .header("Authorization", auth())).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41501));
    }
    @Test void disabledAccountsCannotReadConfigurationOrUpload() throws Exception {
        String bearer = auth();
        jdbc.update("UPDATE `user` SET status='DISABLED' WHERE id=2");
        mvc.perform(get("/api/tags").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/files/images").file("file", image("png")).header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }
    @Test void realHttpMultipartParserEnforcesSizeAndReturnsJson() throws Exception {
        var client = java.net.http.HttpClient.newHttpClient();
        for (int size : new int[]{0, 10 * 1024 * 1024 + 1}) {
            var body = new ByteArrayOutputStream();
            body.write(("--boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n").getBytes());
            body.write(size == 0 ? image("png") : new byte[size]);
            body.write("\r\n--boundary--\r\n".getBytes());
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/api/files/images"))
                    .header("Authorization", auth()).header("Content-Type", "multipart/form-data; boundary=boundary")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(size == 0 ? 200 : 413);
            assertThat(json.readTree(response.body()).path("code").asInt()).isEqualTo(size == 0 ? 0 : 41301);
        }
    }

}
