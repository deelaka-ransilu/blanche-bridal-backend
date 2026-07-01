package com.blanchebridal.backend.auth.service.impl;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.auth.entity.VerificationToken;
import com.blanchebridal.backend.auth.repository.VerificationTokenRepository;
import com.blanchebridal.backend.auth.entity.VerificationTokenType;
import com.blanchebridal.backend.auth.service.AuthService;
import com.blanchebridal.backend.shared.email.EmailService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.blanchebridal.backend.auth.dto.res.AuthResponse;
import com.blanchebridal.backend.auth.dto.req.GoogleAuthRequest;
import com.blanchebridal.backend.auth.dto.req.LoginRequest;
import com.blanchebridal.backend.auth.dto.req.RegisterRequest;
import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.entity.UserStatus;
import com.blanchebridal.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.blanchebridal.backend.auth.dto.res.RefreshResponse;
import com.blanchebridal.backend.auth.entity.RefreshToken;
import com.blanchebridal.backend.auth.repository.RefreshTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${google.client-id}")
    private String googleClientId;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailAndStatusNot(request.email(), UserStatus.INACTIVE)) {
            throw new ConflictException("Email already in use");
        }
        if (userRepository.existsByPhoneAndStatusNot(request.phone(), UserStatus.INACTIVE)) {
            throw new ConflictException("Phone number already in use");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(UserRole.CUSTOMER)
                // status defaults to PENDING_VERIFICATION via @Builder.Default
                .build();

        userRepository.save(user);
        sendVerificationToken(user);
        log.info("[Auth] New customer registered: {} <{}>", user.getFirstName(), user.getEmail());
        return new AuthResponse(null, null, null);
    }
    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (user.getPasswordHash() == null) {
            throw new UnauthorizedException(
                    "This account was created with Google. Please sign in with Google, " +
                            "or use 'Forgot Password' to set a password.");
        }

        if (user.isPendingVerification()) {
            throw new UnauthorizedException(
                    "Please verify your email before logging in. Check your inbox.");
        }

        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new UnauthorizedException(
                    "Your account has been deactivated. Please contact support.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        log.info("[Auth] Login successful for {} ({})", user.getEmail(), user.getRole());

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = issueRefreshToken(user);
        return new AuthResponse(accessToken, user.getRole().name(), refreshToken);
    }

    @Override
    @Transactional
    public AuthResponse googleAuth(GoogleAuthRequest request) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.googleToken());
            if (idToken == null) {
                throw new UnauthorizedException("Invalid Google token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email     = payload.getEmail();
            String googleId  = payload.getSubject();
            String firstName = (String) payload.get("given_name");
            String lastName  = (String) payload.get("family_name");

            boolean isNewUser = !userRepository.existsByEmailAndStatusNot(
                    email, UserStatus.INACTIVE);

            User user = userRepository.findByEmail(email).orElseGet(() ->
                    userRepository.save(User.builder()
                            .email(email)
                            .googleId(googleId)
                            .firstName(firstName != null ? firstName : "")
                            .lastName(lastName   != null ? lastName  : "")
                            .phone("google_" + googleId)
                            .role(UserRole.CUSTOMER)
                            // status defaults to PENDING_VERIFICATION
                            .build())
            );

            if (user.getGoogleId() == null) {
                user.setGoogleId(googleId);
                userRepository.save(user);
            }

            if (isNewUser) {
                sendVerificationToken(user);
                log.info("[Auth] New Google account registered: {} <{}>", user.getFirstName(), email);
                return new AuthResponse(null, null, null);
            }

            if (user.isPendingVerification()) {
                throw new UnauthorizedException(
                        "Please verify your email first. Check your inbox.");
            }

            if (user.getStatus() == UserStatus.INACTIVE) {
                throw new UnauthorizedException(
                        "Your account has been deactivated. Please contact support.");
            }

            log.info("[Auth] Google login successful for {}", email);

            String accessToken = jwtUtil.generateToken(user);
            String refreshToken = issueRefreshToken(user);
            return new AuthResponse(accessToken, user.getRole().name(), refreshToken);

        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Auth] Google auth failed", e);
            throw new UnauthorizedException("Google authentication failed");
        }
    }

    @Override
    @Transactional
    public void verifyEmail(String tokenString) {
        VerificationToken vToken = tokenRepository
                .findByTokenAndType(tokenString, VerificationTokenType.EMAIL_VERIFY)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invalid or expired verification link"));

        if (vToken.isExpired()) {
            tokenRepository.delete(vToken);
            throw new UnauthorizedException(
                    "Verification link has expired. Please request a new one.");
        }

        User user = vToken.getUser();
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        tokenRepository.delete(vToken);
        log.info("[Auth] Email verified for {}", user.getEmail());
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account found with that email"));

        if (user.isActive()) {
            throw new ConflictException("This account is already verified");
        }

        tokenRepository.deleteAllByUserAndType(user, VerificationTokenType.EMAIL_VERIFY);
        sendVerificationToken(user);
        log.info("[Auth] Verification email resent to {}", email);
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            tokenRepository.deleteAllByUserAndType(user, VerificationTokenType.PASSWORD_RESET);
            String tokenString = generateSecureToken();

            VerificationToken resetToken = VerificationToken.builder()
                    .user(user)
                    .token(tokenString)
                    .type(VerificationTokenType.PASSWORD_RESET)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();

            tokenRepository.save(resetToken);
            emailService.sendPasswordResetEmail(email, tokenString);
            log.info("[Auth] Password reset email sent to {}", email);
        });
    }

    @Override
    @Transactional
    public void resetPassword(String tokenString, String newPassword) {
        VerificationToken vToken = tokenRepository
                .findByTokenAndType(tokenString, VerificationTokenType.PASSWORD_RESET)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invalid or expired reset link"));

        if (vToken.isExpired()) {
            tokenRepository.delete(vToken);
            throw new UnauthorizedException(
                    "Reset link has expired. Please request a new one.");
        }

        User user = vToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        tokenRepository.delete(vToken);
        log.info("[Auth] Password reset successful for {}", user.getEmail());
    }

    @Override
    @Transactional
    public RefreshResponse refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!stored.isValid()) {
            // Token expired or revoked — delete it and force re-login
            refreshTokenRepository.delete(stored);
            throw new UnauthorizedException("Refresh token expired. Please log in again.");
        }

        User user = stored.getUser();

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is inactive.");
        }

        // Rotate: delete old, issue new
        refreshTokenRepository.delete(stored);
        issueRefreshToken(user); // new one stored in DB

        String newAccessToken = jwtUtil.generateToken(user);
        log.info("[Auth] Token refreshed for {}", user.getEmail());
        return new RefreshResponse(newAccessToken);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash)
                .ifPresent(refreshTokenRepository::delete);
        log.info("[Auth] Logout — refresh token revoked");
    }

    // Runs every night at 2 AM — cleans up expired/revoked refresh tokens
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked(LocalDateTime.now());
        log.info("[Auth] Expired refresh tokens cleaned up");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String issueRefreshToken(User user) {
        String raw = generateSecureToken();
        String hash = sha256(raw);

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        refreshTokenRepository.save(token);
        return raw; // return raw to controller — only hash is stored
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendVerificationToken(User user) {
        String tokenString = generateSecureToken();
        VerificationToken vToken = VerificationToken.builder()
                .user(user)
                .token(tokenString)
                .type(VerificationTokenType.EMAIL_VERIFY)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        tokenRepository.save(vToken);
        emailService.sendVerificationEmail(user.getEmail(), tokenString);
    }
}