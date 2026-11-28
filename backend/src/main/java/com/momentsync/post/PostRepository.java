package com.momentsync.post;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.momentsync.web.ApiException;
import com.momentsync.web.AuthorResponse;
import com.momentsync.web.PageRequest;
import com.momentsync.web.PageResult;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostRepository {
    private static final String PUBLIC_POST = "p.status='PUBLISHED' AND p.visibility='PUBLIC'";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String POST_WITH_AUTHOR = """
            SELECT p.*, u.nickname, u.avatar_url,
              (SELECT m.media_url FROM post_media m WHERE m.post_id=p.id AND m.media_type='IMAGE'
               ORDER BY m.sort_order,m.id LIMIT 1) AS image_url,
              (SELECT COUNT(*) FROM comment c WHERE c.post_id=p.id AND c.status='PUBLISHED') AS comment_count
            FROM post p JOIN `user` u ON u.id=p.user_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    PostRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    PageResult<PostResponse> search(SearchQuery query) {
        PageRequest pagination = query.pagination();
        var params = new MapSqlParameterSource("limit", pagination.pageSize())
                .addValue("offset", pagination.offset());
        String where = " WHERE " + PUBLIC_POST;
        if (!query.keyword().isEmpty()) {
            // 将通配符和转义符视为普通文字。
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
        var items = readPosts(where + " ORDER BY p.created_at DESC,p.id DESC LIMIT :limit OFFSET :offset", params);
        return new PageResult<>(items, total, pagination.page(), pagination.pageSize());
    }

    PostResponse findPublic(long postId) {
        return readPosts(" WHERE p.id=:id AND " + PUBLIC_POST, new MapSqlParameterSource("id", postId))
                .stream().findFirst().orElseThrow(() -> new ApiException(404, 40401, "印记不存在或已不可见"));
    }

    private List<PostResponse> readPosts(String querySuffix, SqlParameterSource params) {
        var rows = jdbc.query(POST_WITH_AUTHOR + querySuffix, params, (rs, index) -> new PostResponse(
                rs.getString("id"), rs.getString("title"), rs.getString("content"),
                new AuthorResponse(rs.getString("user_id"), rs.getString("nickname"), rs.getString("avatar_url")),
                rs.getString("image_url"), rs.getString("mood"), rs.getString("campus_location"), List.of(),
                rs.getLong("comment_count"), rs.getTimestamp("created_at").toLocalDateTime().format(TIME)));
        if (rows.isEmpty()) {
            return rows;
        }

        Map<String, List<PostResponse.Tag>> tags = new HashMap<>();
        jdbc.query("SELECT pt.post_id,t.id,t.name FROM post_tag pt JOIN tag t ON t.id=pt.tag_id"
                        + " WHERE pt.post_id IN (:ids) AND t.status='ACTIVE' ORDER BY t.id",
                Map.of("ids", rows.stream().map(PostResponse::id).toList()), (RowCallbackHandler) rs ->
                        tags.computeIfAbsent(rs.getString("post_id"), id -> new ArrayList<>())
                                .add(new PostResponse.Tag(rs.getString("id"), rs.getString("name"))));
        return rows.stream().map(post -> new PostResponse(post.id(), post.title(), post.content(),
                post.author(), post.imageUrl(), post.mood(), post.campusLocation(),
                tags.getOrDefault(post.id(), List.of()), post.commentCount(), post.createdAt())).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void requireActiveTags(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        // 锁定选中的标签，使有效性检查与发布写入处于同一事务。
        var active = jdbc.queryForList("SELECT id FROM tag WHERE id IN (:ids) AND status='ACTIVE' ORDER BY id FOR UPDATE",
                Map.of("ids", ids), Long.class);
        if (active.size() != ids.size()) {
            throw ApiException.invalid("标签不存在或已停用，请刷新后重新选择");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    long insert(long userId, CreatePostRequest request) {
        var params = new MapSqlParameterSource("userId", userId)
                .addValue("title", request.title()).addValue("content", request.content())
                .addValue("mood", request.mood()).addValue("location", request.campusLocation());
        var key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO post(user_id,title,content,mood,campus_location,visibility,status)
                VALUES (:userId,:title,:content,:mood,:location,'PUBLIC','PUBLISHED')
                """, params, key, new String[]{"id"});
        long id = Objects.requireNonNull(key.getKey(), "数据库未返回印记 ID").longValue();
        if (request.imageUrl() != null) {
            jdbc.update("INSERT INTO post_media(post_id,media_type,media_url,sort_order) VALUES (:id,'IMAGE',:url,0)",
                    Map.of("id", id, "url", request.imageUrl()));
        }
        if (!request.tagIds().isEmpty()) {
            SqlParameterSource[] tags = request.tagIds().stream()
                    .map(tagId -> new MapSqlParameterSource("id", id).addValue("tagId", tagId))
                    .toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate("INSERT INTO post_tag(post_id,tag_id) VALUES (:id,:tagId)", tags);
        }
        return id;
    }

    public void requirePublic(long postId) {
        requirePublic(postId, "");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockPublic(long postId) {
        requirePublic(postId, " FOR UPDATE");
    }

    private void requirePublic(long postId, String lockClause) {
        var found = jdbc.queryForList("SELECT p.id FROM post p WHERE p.id=:id AND " + PUBLIC_POST + lockClause,
                Map.of("id", postId), Long.class);
        if (found.isEmpty()) {
            throw new ApiException(404, 40401, "印记不存在或已不可见");
        }
    }
}
