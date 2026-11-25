package com.momentsync.post;

import com.momentsync.auth.ActiveAccounts;
import com.momentsync.moderation.ContentPolicy;
import com.momentsync.web.ApiException;
import com.momentsync.web.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PostService {
    private final PostRepository posts;
    private final ActiveAccounts accounts;
    private final ContentPolicy policy;

    PostService(PostRepository posts, ActiveAccounts accounts, ContentPolicy policy) {
        this.posts = posts;
        this.accounts = accounts;
        this.policy = policy;
    }

    @Transactional
    PostResponse publish(long userId, CreatePostRequest request) {
        policy.check(request.title());
        policy.check(request.content());
        accounts.requireActive(userId);
        posts.requireActiveTags(request.tagIds());
        long id = posts.insert(userId, request);
        return posts.findPublic(id);
    }

    @Transactional(readOnly = true)
    PageResult<PostResponse> list(int page, int pageSize) {
        return posts.search(SearchQuery.from("", null, page, pageSize));
    }

    @Transactional(readOnly = true)
    PostResponse detail(long id) {
        if (id <= 0) {
            throw ApiException.invalid("印记 ID 必须为正整数");
        }
        return posts.findPublic(id);
    }
}
