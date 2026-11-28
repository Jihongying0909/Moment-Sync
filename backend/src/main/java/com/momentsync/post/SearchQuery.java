package com.momentsync.post;

import com.momentsync.web.ApiException;
import com.momentsync.web.PageRequest;

record SearchQuery(String keyword, Long tagId, PageRequest pagination) {
    static SearchQuery from(String keyword, Long tagId, int page, int pageSize) {
        String cleaned = keyword == null ? "" : keyword.strip();
        if (cleaned.length() > 100) {
            throw ApiException.invalid("关键词最多 100 个字符");
        }
        if (tagId != null && tagId <= 0) {
            throw ApiException.invalid("标签 ID 必须为正整数");
        }
        return new SearchQuery(cleaned, tagId, new PageRequest(page, pageSize));
    }
}
