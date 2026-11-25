package com.momentsync.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.momentsync.web.ApiException;
import com.momentsync.web.AuthorResponse;
import com.momentsync.web.PageRequest;
import com.momentsync.web.PageResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class AdminRepository {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String POST_SCOPE = "p.visibility IN ('PUBLIC', 'PRIVATE')"
            + " AND p.status IN ('PUBLISHED', 'OFFLINE')";

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

    @Transactional(readOnly = true)
    PageResult<PostRow> posts(String visibility, String status, PageRequest page) {
        Map<String, Object> filters = new HashMap<>();
        String where = " WHERE " + POST_SCOPE;
        if (visibility != null) {
            filters.put("visibility", visibility);
            where += " AND p.visibility = :visibility";
        }
        if (status != null) {
            filters.put("status", status);
            where += " AND p.status = :status";
        }
        long total = jdbc.sql("SELECT COUNT(*) FROM post p" + where).params(filters).query(Long.class).single();
        var items = jdbc.sql("""
                        SELECT p.id, p.title, p.user_id, p.visibility, p.status, p.created_at, u.nickname, u.avatar_url
                        FROM post p JOIN `user` u ON u.id = p.user_id
                        """ + where + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset")
                .params(filters).param("limit", page.pageSize()).param("offset", page.offset())
                .query((rs, row) -> new PostRow(rs.getString("id"), rs.getString("title"), author(rs),
                        rs.getString("visibility"), rs.getString("status"), createdAt(rs)))
                .list();
        return new PageResult<>(items, total, page.page(), page.pageSize());
    }

    PostDetail post(long id) {
        return jdbc.sql("""
                        SELECT p.id, p.title, p.user_id, p.visibility, p.status, p.created_at, u.nickname, u.avatar_url,
                               p.content, p.mood, p.campus_location, t.id AS tag_id, t.name AS tag_name,
                               (SELECT m.media_url FROM post_media m WHERE m.post_id = p.id AND m.media_type = 'IMAGE'
                                ORDER BY m.sort_order, m.id LIMIT 1) AS image_url
                        FROM post p JOIN `user` u ON u.id = p.user_id
                        LEFT JOIN post_tag pt ON pt.post_id = p.id
                        LEFT JOIN tag t ON t.id = pt.tag_id
                        WHERE p.id = :id AND
                        """ + POST_SCOPE + " ORDER BY t.id")
                .param("id", id)
                .query(rs -> {
                    if (!rs.next()) {
                        throw postNotFound();
                    }
                    List<TagRow> tags = new ArrayList<>();
                    var detail = new PostDetail(rs.getString("id"), rs.getString("title"), author(rs),
                            rs.getString("visibility"), rs.getString("status"), createdAt(rs),
                            rs.getString("content"), rs.getString("image_url"), tags,
                            rs.getString("mood"), rs.getString("campus_location"));
                    do {
                        String tagId = rs.getString("tag_id");
                        if (tagId != null) {
                            tags.add(new TagRow(tagId, rs.getString("tag_name")));
                        }
                    } while (rs.next());
                    return detail;
                });
    }

    private static ApiException postNotFound() {
        return new ApiException(404, 40401, "印记不存在或已不可见");
    }

    private static AuthorResponse author(ResultSet rs) throws SQLException {
        return new AuthorResponse(rs.getString("user_id"), rs.getString("nickname"), rs.getString("avatar_url"));
    }

    private static String createdAt(ResultSet rs) throws SQLException {
        return rs.getTimestamp("created_at").toLocalDateTime().format(TIME);
    }

    record UserRow(String id, String username, String nickname, String status, String createdAt) {}

    record PostRow(String id, String title, AuthorResponse author, String visibility, String status, String createdAt) {}

    record PostDetail(String id, String title, AuthorResponse author, String visibility, String status,
                      String createdAt, String content, String imageUrl, List<TagRow> tags, String mood,
                      String campusLocation) {}

    record TagRow(String id, String name) {}
}
