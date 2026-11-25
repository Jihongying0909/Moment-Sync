package com.momentsync.post;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.momentsync.web.ApiException;

record CreatePostRequest(String title, String content, String imageUrl, String mood,
                         String campusLocation, List<Long> tagIds) {
    static CreatePostRequest from(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw ApiException.invalid("请求体必须为 JSON 对象");
        }
        return new CreatePostRequest(requiredText(body, "title", 100), requiredText(body, "content", 2000),
                imageUrl(body), optionalText(body, "mood", 30), optionalText(body, "campusLocation", 100),
                tagIds(body.get("tagIds")));
    }

    private static String requiredText(JsonNode body, String field, int maxLength) {
        String value = optionalText(body, field, maxLength);
        if (value == null || value.replaceAll("[\\p{Z}\\s\\p{Cf}]+", "").isEmpty()) {
            throw ApiException.invalid(field + " 不能为空");
        }
        return value;
    }

    private static String optionalText(JsonNode body, String field, int maxLength) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw ApiException.invalid(field + " 必须为字符串");
        }
        String value = node.textValue().strip();
        if (value.length() > maxLength) {
            throw ApiException.invalid(field + " 最多 " + maxLength + " 个字符");
        }
        return value.isEmpty() ? null : value;
    }

    private static String imageUrl(JsonNode body) {
        String value = optionalText(body, "imageUrl", 500);
        if (value == null) {
            return null;
        }
        try {
            URI uri = new URI(value);
            boolean sitePath = value.startsWith("/") && !value.startsWith("//");
            boolean webUrl = ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
            if (sitePath || webUrl) {
                return value;
            }
        } catch (URISyntaxException ignored) {
            // 无法解析的引用与不支持的 URL 协议使用相同的参数错误。
        }
        throw ApiException.invalid("imageUrl 必须为上传后的 HTTP(S) 地址或站内路径");
    }

    private static List<Long> tagIds(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray() || node.size() > 5) {
            throw ApiException.invalid("tagIds 必须为最多 5 项的字符串数组");
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || !item.textValue().matches("[1-9][0-9]{0,18}")) {
                throw ApiException.invalid("标签 ID 必须为正整数的字符串");
            }
            try {
                ids.add(Long.parseLong(item.textValue()));
            } catch (NumberFormatException error) {
                throw ApiException.invalid("标签 ID 超出 BIGINT 范围");
            }
        }
        return ids.stream().distinct().toList();
    }
}
