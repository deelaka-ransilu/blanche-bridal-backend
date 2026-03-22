package edu.bridalshop.backend.service;

import edu.bridalshop.backend.dto.request.LoginRequest;
import edu.bridalshop.backend.dto.request.RefreshTokenRequest;
import edu.bridalshop.backend.dto.request.RegisterRequest;
import edu.bridalshop.backend.dto.response.AuthResponse;
import edu.bridalshop.backend.dto.response.MessageResponse;
import edu.bridalshop.backend.entity.CustomerProfile;
import edu.bridalshop.backend.entity.RefreshToken;
import edu.bridalshop.backend.entity.User;
import edu.bridalshop.backend.entity.UserRole;
import edu.bridalshop.backend.exception.EmailAlreadyExistsException;
import edu.bridalshop.backend.exception.InvalidCredentialsException;
import edu.bridalshop.backend.exception.ResourceNotFoundException;
import edu.bridalshop.backend.repository.CustomerProfileRepository;
import edu.bridalshop.backend.repository.RefreshTokenRepository;
import edu.bridalshop.backend.repository.UserRepository;
import edu.bridalshop.backend.security.CustomUserDetails;
import edu.bridalshop.backend.security.JwtService;
import edu.bridalshop.backend.util.PayloadSanitizer;
import edu.bridalshop.backend.util.PublicIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bridalshop.backend.dto.request.ForgotPasswordRequest;
import edu.bridalshop.backend.dto.request.ResetPasswordRequest;
import edu.bridalshop.backend.security.TokenService;
import io.jsonwebtoken.Claims;

import edu.bridalshop.backend.dto.request.GoogleAuthRequest;
import edu.bridalshop.backend.security.GoogleTokenVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository             userRepository;
    private final CustomerProfileRepository  customerProfileRepository;
    private final RefreshTokenRepository     refreshTokenRepository;
    private final PasswordEncoder            passwordEncoder;
    private final JwtService                 jwtService;
    private final PublicIdGenerator          publicIdGenerator;
    private final PayloadSanitizer           sanitizer;
    private final TokenService  tokenService;
    private final EmailService  emailService;

    private final GoogleTokenVerifier googleTokenVerifier;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    // ── Register ───────────────────────────────────────────────────────
    @Transactional
    public MessageResponse register(RegisterRequest req) {

        // 1. Sanitize
        req.setFullName(sanitizer.sanitizeText(req.getFullName()));
        req.setEmail(sanitizer.sanitizeEmail(req.getEmail()));
        req.setPhone(sanitizer.sanitizeText(req.getPhone()));

        // 2. Check duplicate email
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new EmailAlreadyExistsException("Email already in use");
        }

        // 3. Create user
        User user = User.builder()
                .publicId(publicIdGenerator.forUser())
                .fullName(req.getFullName())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(UserRole.CUSTOMER)
                .emailVerified(false)
                .profileCompleted(false)
                .isActive(true)
                .build();
        userRepository.save(user);

        // 4. Create customer profile
        CustomerProfile profile = CustomerProfile.builder()
                .user(user)
                .phone(req.getPhone())
                .build();
        customerProfileRepository.save(profile);

        // 5. Send verification email
        String token = tokenService.generateEmailVerificationToken(
                user.getUserId(), user.getEmail());
        emailService.sendVerificationEmail(
                user.getEmail(), user.getFullName(), token);

        return new MessageResponse(
                "Registration successful. Please check your email to verify your account.");
    }

    // ── Login ──────────────────────────────────────────────────────────
    @Transactional
    public AuthResponse login(LoginRequest req) {

        // 1. Sanitize
        req.setEmail(sanitizer.sanitizeEmail(req.getEmail()));

        // 2. Find user
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() ->
                        new InvalidCredentialsException("Invalid email or password"));

        // 3. Check password
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // 4. Check account active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new InvalidCredentialsException("Account is disabled");
        }

        // 5. Generate tokens
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = generateAndSaveRefreshToken(user);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    // ── Refresh Token ──────────────────────────────────────────────────
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest req) {

        RefreshToken stored = refreshTokenRepository
                .findByToken(req.getRefreshToken())
                .orElseThrow(() ->
                        new InvalidCredentialsException("Invalid refresh token"));

        if (stored.getRevoked()) {
            throw new InvalidCredentialsException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException("Refresh token has expired");
        }

        // Rotate — revoke old, issue new
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newAccessToken  = jwtService.generateAccessToken(userDetails);
        String newRefreshToken = generateAndSaveRefreshToken(user);

        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    // ── Logout ─────────────────────────────────────────────────────────
    @Transactional
    public MessageResponse logout(Integer userId) {
        refreshTokenRepository.revokeAllUserTokens(userId);
        return new MessageResponse("Logged out successfully");
    }

    // ── Internal helpers ───────────────────────────────────────────────
    private String generateAndSaveRefreshToken(User user) {
        String token = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now()
                        .plusSeconds(refreshExpirationMs / 1000))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return token;
    }

    private AuthResponse buildAuthResponse(
            User user, String accessToken, String refreshToken) {

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .publicId(user.getPublicId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .emailVerified(user.getEmailVerified())
                .profileCompleted(user.getProfileCompleted())
                .build();
    }

    // ── Send verification email ────────────────────────────────────────
    @Transactional
    public MessageResponse resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return new MessageResponse("Email is already verified");
        }

        String token = tokenService
                .generateEmailVerificationToken(user.getUserId(), user.getEmail());
        emailService.sendVerificationEmail(
                user.getEmail(), user.getFullName(), token);

        return new MessageResponse("Verification email sent");
    }

    // ── Verify email ───────────────────────────────────────────────────
    @Transactional
    public MessageResponse verifyEmail(String token) {
        Claims claims = tokenService.verifyToken(token, "EMAIL_VERIFY");

        String email = claims.getSubject();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        user.setEmailVerified(true);
        userRepository.save(user);

        return new MessageResponse("Email verified successfully");
    }

    // ── Forgot password ────────────────────────────────────────────────
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest req) {
        req.setEmail(sanitizer.sanitizeEmail(req.getEmail()));

        // Always return success — never reveal if email exists
        userRepository.findByEmail(req.getEmail()).ifPresent(user -> {
            String token = tokenService.generatePasswordResetToken(
                    user.getUserId(), user.getEmail());
            emailService.sendPasswordResetEmail(
                    user.getEmail(), user.getFullName(), token);
        });

        return new MessageResponse(
                "If that email exists, a reset link has been sent");
    }

    // ── Reset password ─────────────────────────────────────────────────
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest req) {
        Claims claims = tokenService.verifyToken(req.getToken(), "PASSWORD_RESET");

        String email = claims.getSubject();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        // Revoke all refresh tokens — force re-login
        refreshTokenRepository.revokeAllUserTokens(user.getUserId());

        return new MessageResponse("Password reset successfully. Please log in.");
    }

    // ── Google Login ───────────────────────────────────────────────────
    @Transactional
    public AuthResponse googleLogin(GoogleAuthRequest req) {

        // 1. Verify token with Google — get user info
        GoogleTokenVerifier.GoogleUserInfo googleUser =
                googleTokenVerifier.verify(req.getIdToken());

        // 2. Try to find existing user by google_id first
        User user = userRepository.findByGoogleId(googleUser.googleId)
                .orElseGet(() -> {

                    // 3. Not found by googleId — check if email exists
                    return userRepository.findByEmail(googleUser.email)
                            .map(existingUser -> {
                                // Email exists (registered with password before)
                                // Link Google account to existing user
                                existingUser.setGoogleId(googleUser.googleId);
                                existingUser.setEmailVerified(true);
                                if (existingUser.getProfilePicture() == null) {
                                    existingUser.setProfilePicture(
                                            googleUser.pictureUrl);
                                }
                                return userRepository.save(existingUser);
                            })
                            .orElseGet(() -> {
                                // 4. Brand new user — create account
                                User newUser = User.builder()
                                        .publicId(publicIdGenerator.forUser())
                                        .fullName(googleUser.fullName)
                                        .email(googleUser.email)
                                        .googleId(googleUser.googleId)
                                        .profilePicture(googleUser.pictureUrl)
                                        .role(UserRole.CUSTOMER)
                                        .emailVerified(true)  // Google verified it
                                        .profileCompleted(false)
                                        .isActive(true)
                                        .build();
                                userRepository.save(newUser);

                                // Create customer profile
                                CustomerProfile profile = CustomerProfile.builder()
                                        .user(newUser)
                                        .build();
                                customerProfileRepository.save(profile);

                                return newUser;
                            });
                });

        // 5. Check account is active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new InvalidCredentialsException("Account is disabled");
        }

        // 6. Generate your JWT tokens — same as regular login
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = generateAndSaveRefreshToken(user);

        return buildAuthResponse(user, accessToken, refreshToken);
    }
}