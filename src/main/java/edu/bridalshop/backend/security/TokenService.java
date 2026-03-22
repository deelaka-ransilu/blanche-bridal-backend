package edu.bridalshop.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class TokenService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${app.email.verification-expiry-hours}")
    private long verificationExpiryHours;

    @Value("${app.email.reset-expiry-hours}")
    private long resetExpiryHours;

    // ── Email verification token — expires in 24h ──────────────────────
    public String generateEmailVerificationToken(Integer userId, String email) {
        return Jwts.builder()
                .subject(email)
                .claim("userId",  userId)
                .claim("purpose", "EMAIL_VERIFY")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                        + verificationExpiryHours * 3600 * 1000))
                .signWith(getKey())
                .compact();
    }

    // ── Password reset token — expires in 1h ──────────────────────────
    public String generatePasswordResetToken(Integer userId, String email) {
        return Jwts.builder()
                .subject(email)
                .claim("userId",  userId)
                .claim("purpose", "PASSWORD_RESET")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()
                        + resetExpiryHours * 3600 * 1000))
                .signWith(getKey())
                .compact();
    }

    // ── Verify token + check purpose ───────────────────────────────────
    public Claims verifyToken(String token, String expectedPurpose) {
        Claims claims = Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String purpose = claims.get("purpose", String.class);
        if (!expectedPurpose.equals(purpose)) {
            throw new RuntimeException("Token purpose mismatch");
        }
        return claims;
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}