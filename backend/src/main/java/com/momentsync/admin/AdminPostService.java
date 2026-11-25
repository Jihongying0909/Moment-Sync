package com.momentsync.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdminPostService {
    private final AdminRepository posts;

    AdminPostService(AdminRepository posts) {
        this.posts = posts;
    }

    @Transactional
    PostStatus takeOffline(long id) {
        // 评论写入也锁定这条印记，保证评论与下架按锁的获取顺序执行。
        if ("PUBLISHED".equals(posts.lockPostStatus(id))) {
            posts.markOffline(id);
        }
        return new PostStatus(Long.toString(id), "OFFLINE");
    }

    record PostStatus(String id, String status) {}
}
