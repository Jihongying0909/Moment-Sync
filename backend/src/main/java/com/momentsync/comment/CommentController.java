package com.momentsync.comment;

import com.momentsync.auth.CurrentUserProvider;
import com.momentsync.web.ApiResponse;
import com.momentsync.web.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{id}/comments")
class CommentController {
    private final CommentService comments;
    private final CurrentUserProvider users;

    CommentController(CommentService comments, CurrentUserProvider users) {
        this.comments = comments;
        this.users = users;
    }

    @GetMapping
    ApiResponse<PageResult<CommentResponse>> list(@PathVariable long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        users.requireUserId();
        return ApiResponse.success(comments.list(id, page, pageSize));
    }

    @PostMapping
    ApiResponse<CommentResponse> publish(@PathVariable long id, @RequestBody CreateComment request) {
        return ApiResponse.success(comments.publish(id, users.requireUserId(), request.content()));
    }

    record CreateComment(String content) {}
}
