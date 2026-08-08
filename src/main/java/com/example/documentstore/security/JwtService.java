package com.example.documentstore.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates the JWTs used to authenticate API calls. Configure a
 * stable {@code documentstore.auth.jwt.secret} for production so tokens
 * survive restarts; left unset, a random key is generated per process start
 * (safe default, but every restart invalidates existing sessions).
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(
            @Value("${documentstore.auth.jwt.secret:}") String configuredSecret,
            @Value("${documentstore.auth.jwt.expiration-minutes:120}") long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
        this.key = resolveKey(configuredSecret);
    }

    private static SecretKey resolveKey(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("AUTH: documentstore.auth.jwt.secret not set - generated a random signing key for this "
                    + "process. All existing tokens will be invalidated on every restart; set "
                    + "DOCUMENTSTORE_JWT_SECRET (32+ bytes) for stable sessions across deploys.");
            return Jwts.SIG.HS256.key().build();
        }
        byte[] secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            log.warn("AUTH: documentstore.auth.jwt.secret is shorter than 32 bytes (HS256 requires >= 256 bits) "
                    + "- ignoring it and generating a random key instead. All existing tokens will be "
                    + "invalidated on every restart until a longer secret is configured.");
            return Jwts.SIG.HS256.key().build();
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public Optional<String> extractUsername(String token) {
        try {
            String username = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.ofNullable(username);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
