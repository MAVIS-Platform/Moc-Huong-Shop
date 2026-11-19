package com.mochuong.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies HMAC-SHA256 JWT tokens issued by identity-service.
 */
@Component
public class JwtTokenVerifier {

    private final SecretKey signingKey;

    public JwtTokenVerifier(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public VerifiedUser verify(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("JWT token is missing");
        }
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String userId = claims.getSubject();
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);

        if (userId == null || userId.isBlank() || email == null || email.isBlank() || role == null || role.isBlank()) {
            throw new JwtException("JWT token is missing required identity claims");
        }
        return new VerifiedUser(userId, email, role);
    }

    public record VerifiedUser(String userId, String email, String role) {
    }
}
