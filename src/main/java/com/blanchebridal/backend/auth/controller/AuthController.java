package com.blanchebridal.backend.auth.controller;

import com.blanchebridal.backend.auth.dto.req.*;
import com.blanchebridal.backend.auth.dto.res.AuthResponse;
import com.blanchebridal.backend.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.blanchebridal.backend.auth.dto.res.RefreshResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Registration successful. Please check your email to verify your account."
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        setRefreshTokenCookie(response, authResponse.refreshToken());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "token", authResponse.token(),
                        "role",  authResponse.role()
                )
        ));
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> googleAuth(
            @Valid @RequestBody GoogleAuthRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.googleAuth(request);
        if (authResponse.token() == null) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Please check your email to verify your account."
            ));
        }
        setRefreshTokenCookie(response, authResponse.refreshToken());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "token", authResponse.token(),
                        "role",  authResponse.role()
                )
        ));
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

    // New: refresh endpoint
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        String raw = extractRefreshTokenFromCookie(request);
        if (raw == null) {
            throw new com.blanchebridal.backend.exception.UnauthorizedException(
                    "No refresh token provided");
        }
        RefreshResponse refreshResponse = authService.refresh(raw);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of("token", refreshResponse.accessToken())
        ));
    }

    // New: logout endpoint
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String raw = extractRefreshTokenFromCookie(request);
        if (raw != null) {
            authService.logout(raw);
        }
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully."));
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────

    private void setRefreshTokenCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie("refreshToken", rawToken);
        cookie.setHttpOnly(true);   // JS cannot read this — XSS protection
        cookie.setSecure(false);    // Set true in production (HTTPS only)
        cookie.setPath("/api/auth");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days in seconds
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0); // delete immediately
        response.addCookie(cookie);
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if ("refreshToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}