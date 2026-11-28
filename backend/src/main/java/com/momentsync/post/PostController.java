package com.momentsync.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.momentsync.auth.CurrentUserProvider;
import com.momentsync.web.ApiResponse;
import com.momentsync.web.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PostController {
    private final PostService posts;
    private final CurrentUserProvider users;

    PostController(PostService posts, CurrentUserProvider users) {
        this.posts = posts;
        this.users = users;
    }

    @PostMapping("/api/posts")
    ApiResponse<PostResponse> publish(@RequestBody JsonNode body) {
        return ApiResponse.success(posts.publish(users.requireUserId(), CreatePostRequest.from(body)));
    }

    @GetMapping("/api/posts")
    ApiResponse<PageResult<PostResponse>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        users.requireUserId();
        return ApiResponse.success(posts.list(page, pageSize));
    }

    @GetMapping("/api/posts/{id}")
    ApiResponse<PostResponse> detail(@PathVariable long id) {
        users.requireUserId();
        return ApiResponse.success(posts.detail(id));
    }
}
