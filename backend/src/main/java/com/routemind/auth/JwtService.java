package com.routemind.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and validates JWTs for the single dispatcher account.
 *
 * Tokens are HMAC-SHA256 signed, carry the username as subject, and expire
 * after {@code routemind.auth.token-ttl}. There is no refresh flow and no
 * server-side token store: a signed token that has not expired is valid,
 * and logging out just discards it client-side.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final Duration tokenTtl;

    public JwtService(AuthProperties properties) {
        byte[] secretBytes = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "routemind.auth.jwt-secret must be at least 32 characters long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.tokenTtl = properties.tokenTtl();
        if (properties.usesDevSecret()) {
            log.warn("Using the default development JWT secret — set JWT_SECRET before any shared deployment");
        }
    }

    /** A signed token for the given username, valid for the configured TTL. */
    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * The username a token was issued for, or empty when the token is
     * malformed, expired, or signed with a different secret. Invalid tokens
     * are a normal unauthenticated request, not an error worth logging.
     */
    public Optional<String> extractUsername(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public Duration tokenTtl() {
        return tokenTtl;
    }
}
