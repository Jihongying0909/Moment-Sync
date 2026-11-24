package com.momentsync.app.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(2);

    private final JwtEncoder encoder;
    private final String issuer;

    JwtTokenService(JwtEncoder encoder, @Value("${moment.auth.issuer}") String issuer) {
        this.encoder = encoder;
        this.issuer = issuer;
    }

    /** Called by login services after they verify the account and password. */
    public String issue(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_LIFETIME))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
