package com.momentsync.web;

public record PageRequest(int page, int pageSize) {
    public PageRequest {
        if (page < 1 || page > 100000 || pageSize < 1 || pageSize > 50) {
            throw ApiException.invalid("page 须为 1–100000，pageSize 须为 1–50");
        }
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
