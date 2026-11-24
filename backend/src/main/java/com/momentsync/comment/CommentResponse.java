package com.momentsync.comment;

import com.momentsync.web.AuthorResponse;

record CommentResponse(String id, String postId, AuthorResponse author, String content, String createdAt) {}
