package edu.bridalshop.backend.controller;

import edu.bridalshop.backend.dto.request.LoginRequest;
import edu.bridalshop.backend.dto.request.RefreshTokenRequest;
import edu.bridalshop.backend.dto.request.RegisterRequest;
import edu.bridalshop.backend.dto.response.AuthResponse;
import edu.bridalshop.backend.dto.response.MessageResponse;
import edu.bridalshop.backend.security.CustomUserDetails;
import edu.bridalshop.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import edu.bridalshop.backend.dto.request.ForgotPasswordRequest;
import edu.bridalshop.backend.dto.request.ResetPasswordRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // POST /api/auth/refresh
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    // POST /api/auth/logout  (requires valid access token)
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(authService.logout(userDetails.getUserId()));
    }

    // POST /api/auth/verify-email
    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(
            @RequestParam String token) {
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    // POST /api/auth/resend-verification
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @RequestParam String email) {
        return ResponseEntity.ok(authService.resendVerificationEmail(email));
    }

    // POST /api/auth/forgot-password
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    // POST /api/auth/reset-password
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }
}