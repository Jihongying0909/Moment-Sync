package com.momentsync.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import com.momentsync.web.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ActiveAccounts {
    static final String UNINITIALIZED_PASSWORD = "{BCrypt}REPLACE_BEFORE_LOGIN";
    private static final String PROFILE_COLUMNS = "id, username, nickname, avatar_url, role";

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

    Optional<LoginAccount> findLoginAccount(String username) {
        return jdbc.sql("SELECT " + PROFILE_COLUMNS
                        + ", password_hash FROM `user` WHERE username = :username AND status = 'ACTIVE'")
                .param("username", username)
                .query((rs, rowNumber) -> new LoginAccount(rs.getLong("id"), profile(rs), rs.getString("password_hash")))
                .optional()
                .filter(account -> supportedRole(account.user()));
    }

    Optional<AccountResponse> findProfile(long userId) {
        return jdbc.sql("SELECT " + PROFILE_COLUMNS + " FROM `user` WHERE id = :id AND status = 'ACTIVE'")
                .param("id", userId)
                .query((rs, rowNumber) -> profile(rs))
                .optional()
                .filter(ActiveAccounts::supportedRole);
    }

    void initializeDemoPassword(long userId, String passwordHash) {
        jdbc.sql("""
                UPDATE `user`
                SET password_hash = :passwordHash
                WHERE id = :id AND status = 'ACTIVE' AND password_hash = :placeholder
                """)
                .param("id", userId)
                .param("passwordHash", passwordHash)
                .param("placeholder", UNINITIALIZED_PASSWORD)
                .update();
    }

    private static AccountResponse profile(ResultSet rs) throws SQLException {
        return new AccountResponse(Long.toString(rs.getLong("id")), rs.getString("username"),
                rs.getString("nickname"), rs.getString("avatar_url"), rs.getString("role"));
    }

    private static boolean supportedRole(AccountResponse account) {
        return "USER".equals(account.role()) || "ADMIN".equals(account.role());
    }

    record LoginAccount(long id, AccountResponse user, String passwordHash) {
    }
}
