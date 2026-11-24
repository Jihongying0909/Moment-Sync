package com.momentsync.interaction;

public interface CommentRepository {
    void requireVisiblePost(long postId, boolean lock);
    void requireActiveUser(long userId);
    Models.PageResult<Models.Comment> list(long postId, int page, int pageSize);
    Models.Comment insert(long postId, long userId, String content);
}
