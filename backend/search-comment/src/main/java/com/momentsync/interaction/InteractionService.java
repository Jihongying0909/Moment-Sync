package com.momentsync.interaction;

import org.springframework.transaction.annotation.Transactional;

public class InteractionService {
    private final PostSearchRepository posts;
    private final CommentRepository comments;
    private final ContentPolicy policy;
    public InteractionService(PostSearchRepository posts, CommentRepository comments, ContentPolicy policy) {
        this.posts = posts; this.comments = comments; this.policy = policy;
    }
    @Transactional(readOnly = true)
    public Models.PageResult<Models.Post> search(SearchQuery query) { return posts.search(query); }
    @Transactional(readOnly = true)
    public Models.PageResult<Models.Comment> comments(long postId, int page, int pageSize) {
        validateId(postId); SearchQuery.validatePage(page, pageSize);
        comments.requireVisiblePost(postId, false);
        return comments.list(postId, page, pageSize);
    }
    @Transactional
    public Models.Comment publish(long postId, long userId, String content) {
        validateId(postId);
        String cleaned = content == null ? "" : content.strip();
        if (cleaned.replaceAll("[\\p{Z}\\s\\p{Cf}]+", "").isEmpty() || cleaned.length() > 500)
            throw InteractionException.invalid("评论须为 1–500 个字符，不能只有空白");
        policy.check(cleaned);
        comments.requireActiveUser(userId);
        // 与管理下架在同一 post 行上串行，避免校验完成后在已下架内容上插入评论。
        comments.requireVisiblePost(postId, true);
        return comments.insert(postId, userId, cleaned);
    }
    private void validateId(long id) {
        if (id <= 0) throw InteractionException.invalid("印记 ID 必须为正整数");
    }
}
