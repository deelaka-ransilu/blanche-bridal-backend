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

        // 5. TODO: send verification email (next step)

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
}