package com.momentsync.auth;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
class AccountAuthentication {
    private final ActiveAccounts accounts;

    AccountAuthentication(ActiveAccounts accounts) {
        this.accounts = accounts;
    }

    JwtAuthenticationToken authenticate(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw invalidAccount();
        }
        if (userId <= 0) {
            throw invalidAccount();
        }

        String role = accounts.findRole(userId)
                .orElseThrow(AccountAuthentication::invalidAccount);
        SimpleGrantedAuthority authority = switch (role) {
            case "USER" -> new SimpleGrantedAuthority("ROLE_USER");
            case "ADMIN" -> new SimpleGrantedAuthority("ROLE_ADMIN");
            default -> throw invalidAccount();
        };
        return new JwtAuthenticationToken(jwt, List.of(authority), Long.toString(userId));
    }

    private static InvalidBearerTokenException invalidAccount() {
        return new InvalidBearerTokenException("Account is unavailable");
    }
}
