package com.momentsync.comment;

import com.momentsync.auth.ActiveAccounts;
import com.momentsync.moderation.ContentPolicy;
import com.momentsync.post.PostRepository;
import com.momentsync.web.ApiException;
import com.momentsync.web.PageRequest;
import com.momentsync.web.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CommentService {
    private final PostRepository posts;
    private final CommentRepository comments;
    private final ActiveAccounts accounts;
    private final ContentPolicy policy;

    CommentService(PostRepository posts, CommentRepository comments, ActiveAccounts accounts, ContentPolicy policy) {
        this.posts = posts;
        this.comments = comments;
        this.accounts = accounts;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    PageResult<CommentResponse> list(long postId, int page, int pageSize) {
        validatePostId(postId);
        var pagination = new PageRequest(page, pageSize);
        posts.requirePublic(postId);
        return comments.list(postId, pagination);
    }

    @Transactional
    CommentResponse publish(long postId, long userId, String content) {
        validatePostId(postId);
        String cleaned = content == null ? "" : content.strip();
        if (cleaned.replaceAll("[\\p{Z}\\s\\p{Cf}]+", "").isEmpty() || cleaned.length() > 500) {
            throw ApiException.invalid("评论须为 1–500 个字符，不能只有空白");
        }
        policy.check(cleaned);
        accounts.requireActive(userId);
        // 与管理下架在同一印记行上串行，避免校验后对已下架内容插入评论。
        posts.lockPublic(postId);
        return comments.insert(postId, userId, cleaned);
    }

    private void validatePostId(long id) {
        if (id <= 0) {
            throw ApiException.invalid("印记 ID 必须为正整数");
        }
    }
}
