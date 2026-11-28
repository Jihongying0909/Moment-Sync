package com.momentsync.comment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

import com.momentsync.web.AuthorResponse;
import com.momentsync.web.PageRequest;
import com.momentsync.web.PageResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class CommentRepository {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String COMMENT_WITH_AUTHOR =
            "SELECT c.*,u.nickname,u.avatar_url FROM comment c JOIN `user` u ON u.id=c.user_id";

    private final NamedParameterJdbcTemplate jdbc;

    CommentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    PageResult<CommentResponse> list(long postId, PageRequest pagination) {
        var params = new MapSqlParameterSource("postId", postId)
                .addValue("limit", pagination.pageSize()).addValue("offset", pagination.offset());
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM comment WHERE post_id=:postId AND status='PUBLISHED'", params, Long.class);
        var items = jdbc.query(COMMENT_WITH_AUTHOR
                        + " WHERE c.post_id=:postId AND c.status='PUBLISHED' ORDER BY c.created_at DESC,c.id DESC"
                        + " LIMIT :limit OFFSET :offset",
                params, (rs, index) -> comment(rs));
        return new PageResult<>(items, total, pagination.page(), pagination.pageSize());
    }

    CommentResponse insert(long postId, long userId, String content) {
        var key = new GeneratedKeyHolder();
        var params = new MapSqlParameterSource("postId", postId)
                .addValue("userId", userId).addValue("content", content);
        jdbc.update("INSERT INTO comment(post_id,user_id,content,status) VALUES (:postId,:userId,:content,'PUBLISHED')",
                params, key, new String[]{"id"});
        Number id = Objects.requireNonNull(key.getKey(), "数据库未返回评论 ID");
        return jdbc.queryForObject(COMMENT_WITH_AUTHOR + " WHERE c.id=:id",
                Map.of("id", id.longValue()), (rs, index) -> comment(rs));
    }

    private CommentResponse comment(ResultSet rs) throws SQLException {
        var author = new AuthorResponse(rs.getString("user_id"), rs.getString("nickname"), rs.getString("avatar_url"));
        return new CommentResponse(rs.getString("id"), rs.getString("post_id"), author, rs.getString("content"),
                rs.getTimestamp("created_at").toLocalDateTime().format(TIME));
    }
}
