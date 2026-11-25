package com.momentsync.auth;

import java.nio.charset.StandardCharsets;

import com.momentsync.auth.ActiveAccounts.LoginAccount;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo-accounts")
class DemoAccountInitializer implements ApplicationRunner {
    private static final int MIN_PASSWORD_BYTES = 8;

    private final ActiveAccounts accounts;
    private final BCryptPasswordEncoder passwords;
    private final Environment environment;

    DemoAccountInitializer(ActiveAccounts accounts, BCryptPasswordEncoder passwords, Environment environment) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initialize("student_a", "USER", "DEMO_STUDENT_A_PASSWORD");
        initialize("student_b", "USER", "DEMO_STUDENT_B_PASSWORD");
        initialize("admin_demo", "ADMIN", "DEMO_ADMIN_PASSWORD");
    }

    private void initialize(String username, String expectedRole, String variable) {
        LoginAccount account = accounts.findLoginAccount(username)
                .filter(value -> expectedRole.equals(value.user().role()))
                .orElseThrow(() -> new IllegalStateException("缺少有效的演示账号 " + username + "，请先导入 seed.sql"));
        if (!ActiveAccounts.UNINITIALIZED_PASSWORD.equals(account.passwordHash())) {
            return;
        }

        String password = environment.getRequiredProperty(variable);
        int size = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.isBlank() || size < MIN_PASSWORD_BYTES || size > LoginService.BCRYPT_MAX_PASSWORD_BYTES) {
            throw new IllegalStateException(variable + " 需要设置至少 8、最多 72 个 UTF-8 字节的非空密码");
        }
        accounts.initializeDemoPassword(account.id(), passwords.encode(password));
    }
}
