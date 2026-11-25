package com.momentsync.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momentsync.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreatePostRequestTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void normalizesOptionalFieldsAndDeduplicatesStringIds() throws Exception {
        var body = json.readTree("""
                {"title":" 晚霞 ","content":" 一起记录 ","imageUrl":" ","mood":null,
                 "campusLocation":" 运动场 ","tagIds":["2","2","9007199254740993"]}
                """);
        var request = CreatePostRequest.from(body);
        assertThat(request.title()).isEqualTo("晚霞");
        assertThat(request.content()).isEqualTo("一起记录");
        assertThat(request.imageUrl()).isNull();
        assertThat(request.mood()).isNull();
        assertThat(request.campusLocation()).isEqualTo("运动场");
        assertThat(request.tagIds()).containsExactly(2L, 9007199254740993L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.test/images/photo.jpg?version=1", "http://localhost:8080/uploads/photo.jpg",
            "/uploads/photo.jpg"
    })
    void acceptsUploadedImageReferences(String imageUrl) {
        var body = json.createObjectNode().put("title", "标题").put("content", "正文").put("imageUrl", imageUrl);
        assertThat(CreatePostRequest.from(body).imageUrl()).isEqualTo(imageUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "wxfile://tmp/photo.jpg", "file:///tmp/photo.jpg", "javascript:alert(1)", "//example.test/a.jpg",
            "http://", "http://user:pass@example.test/a.jpg", "/bad path.jpg"
    })
    void rejectsTemporaryPathsAndInvalidImageReferences(String imageUrl) {
        var body = json.createObjectNode().put("title", "标题").put("content", "正文").put("imageUrl", imageUrl);
        assertThatThrownBy(() -> CreatePostRequest.from(body)).isInstanceOf(ApiException.class);
    }
}
