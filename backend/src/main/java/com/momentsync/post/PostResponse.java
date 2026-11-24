package com.momentsync.post;

import java.util.List;

import com.momentsync.web.AuthorResponse;

record PostResponse(String id, String title, String content, AuthorResponse author, String imageUrl,
                    String mood, String campusLocation, List<Tag> tags, long commentCount, String createdAt) {
    record Tag(String id, String name) {}
}
