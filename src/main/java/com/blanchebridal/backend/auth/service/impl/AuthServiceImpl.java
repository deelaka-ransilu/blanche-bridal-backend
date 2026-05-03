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
import com.blanchebridal.backend.user.repository.UserRepository;
import com.blanchebridal.backend.user.entity.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Value("${google.client-id}")
    private String googleClientId;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(UserRole.CUSTOMER)
                .isActive(false)
                .build();

        userRepository.save(user);
        sendVerificationToken(user);
        log.info("[Auth] New customer registered: {} <{}>", user.getFirstName(), user.getEmail());

        return new AuthResponse(null, null);
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

        if (!user.getIsActive()) {
            throw new UnauthorizedException(
                    "Please verify your email before logging in. Check your inbox.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        log.info("[Auth] Login successful for {} ({})", user.getEmail(), user.getRole());
        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token, user.getRole().name());
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

            boolean isNewUser = !userRepository.existsByEmail(email);

            User user = userRepository.findByEmail(email).orElseGet(() ->
                    userRepository.save(User.builder()
                            .email(email)
                            .googleId(googleId)
                            .firstName(firstName != null ? firstName : "")
                            .lastName(lastName   != null ? lastName  : "")
                            .role(UserRole.CUSTOMER)
                            .isActive(false)
                            .build())
            );

            if (user.getGoogleId() == null) {
                user.setGoogleId(googleId);
                userRepository.save(user);
            }

            if (isNewUser) {
                sendVerificationToken(user);
                log.info("[Auth] New Google account registered: {} <{}>", user.getFirstName(), email);
                return new AuthResponse(null, null);
            }

            if (!user.getIsActive()) {
                throw new UnauthorizedException(
                        "Please verify your email first. Check your inbox.");
            }

            log.info("[Auth] Google login successful for {}", email);
            String token = jwtUtil.generateToken(user);
            return new AuthResponse(token, user.getRole().name());

        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
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
        user.setIsActive(true);
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

        if (user.getIsActive()) {
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
        user.setIsActive(true);
        userRepository.save(user);
        tokenRepository.delete(vToken);
        log.info("[Auth] Password reset successful for {}", user.getEmail());
    }

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
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build();

        tokenRepository.save(vToken);
        emailService.sendVerificationEmail(user.getEmail(), tokenString);
    }
}