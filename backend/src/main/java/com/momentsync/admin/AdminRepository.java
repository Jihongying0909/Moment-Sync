package com.momentsync.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.momentsync.web.PageRequest;
import com.momentsync.web.PageResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class AdminRepository {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcClient jdbc;

    AdminRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    PageResult<UserRow> users(String keyword, String status, PageRequest page) {
        Map<String, Object> filters = new HashMap<>();
        String from = " FROM `user` WHERE role = 'USER' AND status IN ('ACTIVE', 'DISABLED')";
        if (!keyword.isEmpty()) {
            String literal = keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            filters.put("keyword", "%" + literal + "%");
            from += " AND (username LIKE :keyword ESCAPE '!' OR nickname LIKE :keyword ESCAPE '!')";
        }
        if (status != null) {
            filters.put("status", status);
            from += " AND status = :status";
        }
        long total = jdbc.sql("SELECT COUNT(*)" + from).params(filters).query(Long.class).single();
        var items = jdbc.sql("SELECT id, username, nickname, status, created_at" + from
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(filters).param("limit", page.pageSize()).param("offset", page.offset())
                .query((rs, row) -> new UserRow(rs.getString("id"), rs.getString("username"),
                        rs.getString("nickname"), rs.getString("status"), createdAt(rs)))
                .list();
        return new PageResult<>(items, total, page.page(), page.pageSize());
    }

    private static String createdAt(ResultSet rs) throws SQLException {
        return rs.getTimestamp("created_at").toLocalDateTime().format(TIME);
    }

    record UserRow(String id, String username, String nickname, String status, String createdAt) {}
}
