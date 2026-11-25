package com.momentsync.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momentsync.web.ApiResponse;
import com.momentsync.web.ApiException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    private static final RequestMatcher LOGIN_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/admin/auth/login"));

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountAuthentication accounts,
                                            ObjectMapper json, UrlBasedCorsConfigurationSource cors)
            throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            writeError(response, json, 401, 40101, "请先登录");
        };

        DefaultBearerTokenResolver bearerTokens = new DefaultBearerTokenResolver();
        // The API accepts bearer headers and does not use cookies or server sessions.
        return http.cors(config -> config.configurationSource(cors))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/images/*").permitAll()
                        .requestMatchers(LOGIN_ENDPOINTS).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, json, 403, 40301, "没有操作权限")))
                .oauth2ResourceServer(server -> server.authenticationEntryPoint(unauthorized)
                        .bearerTokenResolver(request -> LOGIN_ENDPOINTS.matches(request) ? null : bearerTokens.resolve(request))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(accounts::authenticate)))
                .build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${moment.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtSecretKey(@Value("${moment.auth.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secret.isBlank() || bytes.length < 32 || secret.equals("replace_with_a_long_random_local_secret")) {
            throw new IllegalArgumentException("JWT_SECRET must be a random secret of at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, @Value("${moment.auth.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(issuer),
                new JwtTimestampValidator(Duration.ZERO),
                jwt -> jwt.getExpiresAt() == null
                        ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing expiration", null))
                        : OAuth2TokenValidatorResult.success()));
        return decoder;
    }

    @Bean
    CurrentUserProvider currentUserProvider() {
        return () -> {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                    && token.isAuthenticated()) {
                return Long.parseLong(token.getName());
            }
            throw new ApiException(401, 40101, "请先登录");
        };
    }

    private static void writeError(HttpServletResponse response, ObjectMapper json,
                                   int status, int code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
    }
}
