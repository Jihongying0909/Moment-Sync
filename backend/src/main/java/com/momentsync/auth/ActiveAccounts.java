package com.momentsync.auth;

import java.util.Optional;

import com.momentsync.web.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ActiveAccounts {
    private final JdbcClient jdbc;

    ActiveAccounts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<String> findRole(long userId) {
        return jdbc.sql("SELECT role FROM `user` WHERE id = :id AND status = 'ACTIVE'")
                .param("id", userId)
                .query(String.class)
                .optional();
    }

    public void requireActive(long userId) {
        if (findRole(userId).isEmpty()) {
            throw new ApiException(401, 40101, "登录已失效，请重新登录");
        }
    }
}
