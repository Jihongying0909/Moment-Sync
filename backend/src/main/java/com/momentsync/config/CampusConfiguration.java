package com.momentsync.config;

import java.util.List;
import com.momentsync.auth.CurrentUserProvider;
import com.momentsync.web.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo 预设选项；标签取数据库，心情与地点可由部署配置覆盖。 */
@RestController
public class CampusConfiguration {
    private final JdbcTemplate jdbc;
    private final CurrentUserProvider users;
    private final List<String> moods;
    private final List<String> locations;

    public CampusConfiguration(JdbcTemplate jdbc, CurrentUserProvider users,
            @Value("${moment.campus.moods:开心,平静,期待,充实,疲惫,难过}") List<String> moods,
            @Value("${moment.campus.locations:图书馆,运动场,活动中心,教学楼,食堂,宿舍}") List<String> locations) {
        this.jdbc = jdbc;
        this.users = users;
        this.moods = normalize(moods, 30);
        this.locations = normalize(locations, 100);
    }

    private static List<String> normalize(List<String> values, int maxLength) {
        var result = values.stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
        if (result.isEmpty() || result.stream().anyMatch(s -> s.length() > maxLength)) {
            throw new IllegalArgumentException("校园配置不能为空或超出数据库字段长度");
        }
        return result;
    }

    @GetMapping("/api/tags")
    public ApiResponse<List<Tag>> tags() {
        users.requireUserId();
        return ApiResponse.success(jdbc.query("SELECT id,name FROM tag WHERE status='ACTIVE' ORDER BY id",
                (rs, row) -> new Tag(rs.getString("id"), rs.getString("name"))));
    }

    @GetMapping("/api/moods")
    public ApiResponse<List<String>> moods() {
        users.requireUserId();
        return ApiResponse.success(moods);
    }

    @GetMapping("/api/locations")
    public ApiResponse<List<String>> locations() {
        users.requireUserId();
        return ApiResponse.success(locations);
    }

    public record Tag(String id, String name) {}
}
