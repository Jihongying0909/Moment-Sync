package com.momentsync.post;

import com.momentsync.auth.CurrentUserProvider;
import com.momentsync.web.ApiResponse;
import com.momentsync.web.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PostSearchController {
    private final PostRepository posts;
    private final CurrentUserProvider users;

    PostSearchController(PostRepository posts, CurrentUserProvider users) {
        this.posts = posts;
        this.users = users;
    }

    @GetMapping("/api/posts/search")
    ApiResponse<PageResult<PostResponse>> search(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long tagId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        users.requireUserId();
        return ApiResponse.success(posts.search(SearchQuery.from(keyword, tagId, page, pageSize)));
    }
}
