package com.mochuong.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtTokenVerifierTest {

    private static final String SECRET = "moc-huong-shop-super-secret-jwt-key-minimum-32-bytes-long-2026";
    private static final String WRONG_SECRET = "completely-different-secret-key-at-least-32-bytes-long-9999";

    private final JwtTokenVerifier verifier = new JwtTokenVerifier(SECRET);

    @Test
    void verify_validToken_returnsVerifiedUser() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject("33333333-3333-3333-3333-333333333333")
                .claim("email", "customer@mochuong.vn")
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();

        JwtTokenVerifier.VerifiedUser user = verifier.verify(token);

        assertEquals("33333333-3333-3333-3333-333333333333", user.userId());
        assertEquals("customer@mochuong.vn", user.email());
        assertEquals("CUSTOMER", user.role());
    }

    @Test
    void verify_invalidSignature_throwsJwtException() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(WRONG_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String forgedToken = Jwts.builder()
                .subject("33333333-3333-3333-3333-333333333333")
                .claim("email", "customer@mochuong.vn")
                .claim("role", "ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(wrongKey)
                .compact();

        assertThrows(JwtException.class, () -> verifier.verify(forgedToken));
    }

    @Test
    void verify_missingClaims_throwsJwtException() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String incompleteToken = Jwts.builder()
                .subject("33333333-3333-3333-3333-333333333333")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();

        assertThrows(JwtException.class, () -> verifier.verify(incompleteToken));
    }
}
