package com.blanchebridal.backend.auth.security;

import com.blanchebridal.backend.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expiration;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expiration = expiration;
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role",      user.getRole().name())
                .claim("userId",    user.getId().toString())
                .claim("firstName", user.getFirstName())
                .claim("lastName",  user.getLastName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the token's signature and structure.
     *
     * IMPORTANT: this no longer swallows ExpiredJwtException — it now
     * propagates to the caller (JwtFilter), which needs to distinguish
     * "expired" (→ 401, triggers client-side refresh) from "malformed/
     * tampered" (→ pass-through, eventual 403 via SecurityConfig). See
     * CURRENT_STATE.md Issue #8.
     *
     * Any other parsing failure (bad signature, malformed compact string,
     * unsupported token, illegal argument) still throws — callers that only
     * care about a boolean should catch broadly themselves; JwtFilter now
     * does that catch explicitly instead of this method doing it silently.
     */
    public boolean validateToken(String token) {
        Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
        return true;
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public String extractUserId(String token) {
        return getClaims(token).get("userId", String.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}