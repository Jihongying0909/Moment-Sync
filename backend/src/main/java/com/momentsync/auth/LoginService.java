package com.momentsync.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

import com.momentsync.auth.ActiveAccounts.LoginAccount;
import com.momentsync.web.ApiException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class LoginService {
    static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[aby]?\\$(?:0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");

    private final ActiveAccounts accounts;
    private final BCryptPasswordEncoder passwords;
    private final JwtTokenService tokens;
    private final String dummyHash;

    LoginService(ActiveAccounts accounts, BCryptPasswordEncoder passwords, JwtTokenService tokens) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode("unused-password-for-missing-account");
    }

    LoginResponse login(String username, String password) {
        return issue(verifyCredentials(username, password));
    }

    LoginResponse adminLogin(String username, String password) {
        LoginAccount account = verifyCredentials(username, password);
        if (!"ADMIN".equals(account.user().role())) {
            throw new ApiException(403, 40301, "没有管理员权限");
        }
        return issue(account);
    }

    AccountResponse currentUser(long userId) {
        return accounts.findProfile(userId)
                .orElseThrow(() -> new ApiException(401, 40101, "登录已失效，请重新登录"));
    }

    private LoginAccount verifyCredentials(String username, String password) {
        String normalizedUsername = username == null ? "" : username.strip();
        if (normalizedUsername.isBlank()
                || normalizedUsername.codePointCount(0, normalizedUsername.length()) > MAX_USERNAME_LENGTH) {
            throw ApiException.invalid("请输入有效的账号，最多 50 个字符");
        }
        if (password == null || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw ApiException.invalid("请输入有效的密码，最多 72 个 UTF-8 字节");
        }

        Optional<LoginAccount> account = accounts.findLoginAccount(normalizedUsername);
        Optional<String> hash = account.map(LoginAccount::passwordHash)
                .filter(value -> BCRYPT_HASH.matcher(value).matches());
        // Run BCrypt even for accounts that are missing, disabled, or not initialized.
        boolean passwordMatches = passwords.matches(password, hash.orElse(dummyHash));
        if (hash.isEmpty() || !passwordMatches) {
            throw new ApiException(401, 40101, "账号或密码不正确");
        }
        return account.orElseThrow();
    }

    private LoginResponse issue(LoginAccount account) {
        return new LoginResponse(tokens.issue(account.id()), account.user());
    }
}
