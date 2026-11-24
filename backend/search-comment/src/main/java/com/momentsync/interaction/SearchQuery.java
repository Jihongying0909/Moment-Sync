package com.momentsync.interaction;

public record SearchQuery(String keyword, Long tagId, int page, int pageSize) {
    public SearchQuery {
        keyword = keyword == null ? "" : keyword.strip();
        if (keyword.length() > 100) throw InteractionException.invalid("关键词最多 100 个字符");
        if (tagId != null && tagId <= 0) throw InteractionException.invalid("标签 ID 必须为正整数");
        validatePage(page, pageSize);
    }
    public int offset() { return (page - 1) * pageSize; }
    public static void validatePage(int page, int pageSize) {
        if (page < 1 || page > 100000 || pageSize < 1 || pageSize > 50)
            throw InteractionException.invalid("page 须为 1–100000，pageSize 须为 1–50");
    }
}
