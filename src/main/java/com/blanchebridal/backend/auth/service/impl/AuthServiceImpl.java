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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;

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

    // --- REGISTER ---
    // Change: isActive starts FALSE, email sent, no JWT returned yet
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
                .isActive(false) // ← not active until email verified
                .build();

        userRepository.save(user);
        sendVerificationToken(user); // ← generate token + send email

        // Return null token — user must verify email before logging in
        return new AuthResponse(null, null);
    }

    // --- LOGIN ---
    // Change: added check for unverified email + null password (Google account)
    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        // Google-only account — no password was ever set
        if (user.getPasswordHash() == null) {
            throw new UnauthorizedException(
                    "This account was created with Google. Please sign in with Google, " +
                            "or use 'Forgot Password' to set a password."
            );
        }

        // Email not verified yet
        if (!user.getIsActive()) {
            throw new UnauthorizedException(
                    "Please verify your email before logging in. Check your inbox."
            );
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token, user.getRole().name());
    }

    // --- GOOGLE AUTH ---
    // Change: new Google accounts start with isActive=false, verification email sent
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
                            .isActive(false) // ← same rule: verify first
                            .build())
            );

            // Link googleId if they registered by email first
            if (user.getGoogleId() == null) {
                user.setGoogleId(googleId);
                userRepository.save(user);
            }

            // Send verification email to brand new Google accounts
            if (isNewUser) {
                sendVerificationToken(user);
                return new AuthResponse(null, null); // must verify first
            }

            // Existing account not yet verified
            if (!user.getIsActive()) {
                throw new UnauthorizedException(
                        "Please verify your email first. Check your inbox."
                );
            }

            String token = jwtUtil.generateToken(user);
            return new AuthResponse(token, user.getRole().name());

        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Google authentication failed");
        }
    }

    // --- VERIFY EMAIL ---
    // User clicked the link in their email — token string comes from URL param
    @Override
    @Transactional
    public void verifyEmail(String tokenString) {
        VerificationToken vToken = tokenRepository
                .findByTokenAndType(tokenString, VerificationTokenType.EMAIL_VERIFY)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired verification link"));

        if (vToken.isExpired()) {
            tokenRepository.delete(vToken);
            throw new UnauthorizedException("Verification link has expired. Please request a new one.");
        }

        // Activate the user account
        User user = vToken.getUser();
        user.setIsActive(true);
        userRepository.save(user);

        // Token used — delete it so it can't be used again
        tokenRepository.delete(vToken);
    }

    // --- RESEND VERIFICATION ---
    // User didn't get the email or it expired — send a fresh one
    @Override
    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with that email"));

        if (user.getIsActive()) {
            throw new ConflictException("This account is already verified");
        }

        // Delete any old tokens first, then send fresh one
        tokenRepository.deleteAllByUserAndType(user, VerificationTokenType.EMAIL_VERIFY);
        sendVerificationToken(user);
    }

    // --- FORGOT PASSWORD ---
    // Note: we always return success even if email not found
    // (security best practice — don't reveal if an email exists in the system)
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
        });
    }

    // --- RESET PASSWORD ---
    // User submitted the form with their new password
    @Override
    @Transactional
    public void resetPassword(String tokenString, String newPassword) {
        VerificationToken vToken = tokenRepository
                .findByTokenAndType(tokenString, VerificationTokenType.PASSWORD_RESET)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired reset link"));

        if (vToken.isExpired()) {
            tokenRepository.delete(vToken);
            throw new UnauthorizedException("Reset link has expired. Please request a new one.");
        }

        User user = vToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // If they reset password, their email is confirmed by definition
        user.setIsActive(true);
        userRepository.save(user);

        tokenRepository.delete(vToken);
    }

    // --- PRIVATE HELPERS ---

    // Generates a cryptographically secure random token string
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Creates a 5-minute EMAIL_VERIFY token and sends the email
    private void sendVerificationToken(User user) {
        String tokenString = generateSecureToken();

        VerificationToken vToken = VerificationToken.builder()
                .user(user)
                .token(tokenString)
                .type(VerificationTokenType.EMAIL_VERIFY)
                .expiresAt(LocalDateTime.now().plusMinutes(20)) // ← 20 minutes as requested
                .build();

        tokenRepository.save(vToken);
        emailService.sendVerificationEmail(user.getEmail(), tokenString);
    }
}