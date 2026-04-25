package com.blanchebridal.backend.auth.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.auth.dto.res.AuthResponse;
import com.blanchebridal.backend.auth.dto.req.*;
import com.blanchebridal.backend.auth.service.AuthService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        // Don't return a token — user must verify email first
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Registration successful. Please check your email to verify your account."
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleAuth(
            @Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = authService.googleAuth(request);

        // New Google user — needs to verify email
        if (response.token() == null) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Please check your email to verify your account."
            ));
        }

        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyEmail(
            @RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Email verified successfully. You can now log in."
        ));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, Object>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Verification email sent. Please check your inbox."
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        // Always return success — never reveal if email exists
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "If an account exists with that email, a reset link has been sent."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password reset successfully. You can now log in."
        ));
    }

    @PostMapping("/setup-superadmin")
    public ResponseEntity<Map<String, Object>> setupSuperadmin(
            @Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new com.blanchebridal.backend.exception.ConflictException("Superadmin already exists");
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(UserRole.SUPERADMIN)
                .isActive(true) // superadmin bypasses email verification
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(Map.of("success", true, "data",
                new AuthResponse(token, user.getRole().name())));
    }
}