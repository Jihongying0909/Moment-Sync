package com.momentsync.web;

// BIGINT 标识符作为字符串输出，避免小程序 Number 精度丢失。
public record AuthorResponse(String id, String nickname, String avatarUrl) {}
