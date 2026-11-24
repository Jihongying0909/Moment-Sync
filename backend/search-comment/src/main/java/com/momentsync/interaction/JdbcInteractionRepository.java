package com.momentsync.interaction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public class JdbcInteractionRepository implements PostSearchRepository, CommentRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public JdbcInteractionRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Models.PageResult<Models.Post> search(SearchQuery query) {
        var params = new MapSqlParameterSource("limit", query.pageSize()).addValue("offset", query.offset());
        String where = " WHERE p.status='PUBLISHED' AND p.visibility='PUBLIC'";
        if (!query.keyword().isEmpty()) {
            // 将用户输入的 %, _ 和转义符视为普通文字；所有值使用绑定参数。
            String literal = query.keyword().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            params.addValue("keyword", "%" + literal + "%");
            where += " AND (p.title LIKE :keyword ESCAPE '!' OR p.content LIKE :keyword ESCAPE '!')";
        }
        if (query.tagId() != null) {
            params.addValue("tagId", query.tagId());
            where += " AND EXISTS (SELECT 1 FROM post_tag pt JOIN tag t ON t.id=pt.tag_id"
                    + " WHERE pt.post_id=p.id AND pt.tag_id=:tagId AND t.status='ACTIVE')";
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM post p" + where, params, Long.class);
        String sql = """
            SELECT p.*, u.nickname, u.avatar_url,
              (SELECT m.media_url FROM post_media m WHERE m.post_id=p.id AND m.media_type='IMAGE'
               ORDER BY m.sort_order,m.id LIMIT 1) AS image_url,
              (SELECT COUNT(*) FROM comment c WHERE c.post_id=p.id AND c.status='PUBLISHED') AS comment_count
            FROM post p JOIN `user` u ON u.id=p.user_id
            """ + where + " ORDER BY p.created_at DESC,p.id DESC LIMIT :limit OFFSET :offset";
        var rows = jdbc.query(sql, params, (rs, index) -> new Models.Post(rs.getString("id"),
                rs.getString("title"), rs.getString("content"), author(rs), rs.getString("image_url"),
                rs.getString("mood"), rs.getString("campus_location"), List.of(),
                rs.getLong("comment_count"), time(rs)));
        if (rows.isEmpty()) return new Models.PageResult<>(rows, total, query.page(), query.pageSize());
        // 批量取标签，避免逐条查询；EXISTS 筛选避免多标签导致结果重复。
        Map<String, List<Models.Tag>> tags = new HashMap<>();
        jdbc.query("SELECT pt.post_id,t.id,t.name FROM post_tag pt JOIN tag t ON t.id=pt.tag_id"
                + " WHERE pt.post_id IN (:ids) AND t.status='ACTIVE' ORDER BY t.id",
                Map.of("ids", rows.stream().map(Models.Post::id).toList()), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                    tags.computeIfAbsent(rs.getString("post_id"), id -> new ArrayList<>())
                            .add(new Models.Tag(rs.getString("id"), rs.getString("name"))));
        var items = rows.stream().map(p -> new Models.Post(p.id(), p.title(), p.content(), p.author(), p.imageUrl(),
                p.mood(), p.campusLocation(), tags.getOrDefault(p.id(), List.of()), p.commentCount(), p.createdAt())).toList();
        return new Models.PageResult<>(items, total, query.page(), query.pageSize());
    }
    @Override public void requireVisiblePost(long postId, boolean lock) {
        var found = jdbc.queryForList("SELECT id FROM post WHERE id=:id AND status='PUBLISHED'"
                + " AND visibility='PUBLIC'" + (lock ? " FOR UPDATE" : ""), Map.of("id", postId), Long.class);
        if (found.isEmpty()) throw InteractionException.unavailable();
    }
    @Override public void requireActiveUser(long userId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE id=:id AND status='ACTIVE'",
                Map.of("id", userId), Long.class);
        if (count == null || count == 0) throw new InteractionException(401, 40101, "登录已失效，请重新登录");
    }
    @Override public Models.PageResult<Models.Comment> list(long postId, int page, int pageSize) {
        var params = new MapSqlParameterSource("postId", postId).addValue("limit", pageSize)
                .addValue("offset", (page - 1) * pageSize);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM comment WHERE post_id=:postId AND status='PUBLISHED'", params, Long.class);
        var items = jdbc.query("SELECT c.*,u.nickname,u.avatar_url FROM comment c JOIN `user` u ON u.id=c.user_id"
                + " WHERE c.post_id=:postId AND c.status='PUBLISHED' ORDER BY c.created_at DESC,c.id DESC"
                + " LIMIT :limit OFFSET :offset", params, (rs, n) -> comment(rs));
        return new Models.PageResult<>(items, total, page, pageSize);
    }
    @Override public Models.Comment insert(long postId, long userId, String content) {
        var key = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO comment(post_id,user_id,content,status) VALUES (:postId,:userId,:content,'PUBLISHED')",
                new MapSqlParameterSource("postId", postId).addValue("userId", userId).addValue("content", content), key, new String[]{"id"});
        Number id = Objects.requireNonNull(key.getKey(), "数据库未返回评论 ID");
        return jdbc.queryForObject("SELECT c.*,u.nickname,u.avatar_url FROM comment c JOIN `user` u ON u.id=c.user_id WHERE c.id=:id",
                Map.of("id", id.longValue()), (rs, n) -> comment(rs));
    }
    private Models.Comment comment(ResultSet rs) throws SQLException {
        return new Models.Comment(rs.getString("id"), rs.getString("post_id"), author(rs), rs.getString("content"), time(rs));
    }
    private Models.Author author(ResultSet rs) throws SQLException {
        return new Models.Author(rs.getString("user_id"), rs.getString("nickname"), rs.getString("avatar_url"));
    }
    private String time(ResultSet rs) throws SQLException { return rs.getTimestamp("created_at").toLocalDateTime().format(TIME); }
}
