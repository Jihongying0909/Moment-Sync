package com.momentsync.interaction;

import java.util.List;

public final class Models {
    private Models() {}
    public record Envelope<T>(int code, String message, T data) {
        public static <T> Envelope<T> ok(T data) { return new Envelope<>(0, "success", data); }
    }
    public record PageResult<T>(List<T> items, long total, int page, int pageSize) {}
    // BIGINT 标识符作为字符串输出，避免小程序 Number 精度丢失。
    public record Author(String id, String nickname, String avatarUrl) {}
    public record Tag(String id, String name) {}
    public record Post(String id, String title, String content, Author author, String imageUrl,
                       String mood, String campusLocation, List<Tag> tags, long commentCount,
                       String createdAt) {}
    public record Comment(String id, String postId, Author author, String content, String createdAt) {}
    public record CreateComment(String content) {}
}
