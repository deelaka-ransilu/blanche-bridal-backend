package com.blanchebridal.backend.auth.controller;

import com.blanchebridal.backend.auth.dto.req.ForgotPasswordRequest;
import com.blanchebridal.backend.auth.dto.req.GoogleAuthRequest;
import com.blanchebridal.backend.auth.dto.req.LoginRequest;
import com.blanchebridal.backend.auth.dto.req.RegisterRequest;
import com.blanchebridal.backend.auth.dto.req.ResendVerificationRequest;
import com.blanchebridal.backend.auth.dto.req.ResetPasswordRequest;
import com.blanchebridal.backend.auth.dto.res.AuthResponse;
import com.blanchebridal.backend.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // ── TC-AU-01: Successful customer registration ────────────────────────────
    // RegisterRequest field order (confirmed from source): email, password, firstName, lastName, phone
    // AuthController.register() discards the returned AuthResponse and replies with a
    // hardcoded message — confirmed against the real controller — so no "$.data" here.
    @Test
    @DisplayName("TC-AU-01: Register with valid details returns success message")
    void register_withValidRequest_returnsSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "jane@example.com", "SecurePass123!",
                "Jane", "Doe", "0771234567"
        );

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse("mock.jwt.token", "CUSTOMER"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "Registration successful. Please check your email to verify your account."));
    }

    // ── TC-AU-02: Registration with missing fields returns 400 ────────────────
    @Test
    @DisplayName("TC-AU-02: Register with missing email returns 400 Bad Request")
    void register_withMissingEmail_returnsBadRequest() throws Exception {
        String invalidJson = """
                {
                    "firstName": "Jane",
                    "lastName": "Doe",
                    "password": "SecurePass123!"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // ── TC-AU-03: Successful login returns JWT token ──────────────────────────
    @Test
    @DisplayName("TC-AU-03: Login with valid credentials returns JWT token")
    void login_withValidCredentials_returnsToken() throws Exception {
        LoginRequest request = new LoginRequest("jane@example.com", "SecurePass123!");
        AuthResponse authResponse = new AuthResponse("mock.jwt.token", "CUSTOMER");

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("mock.jwt.token"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
    }

    // ── TC-AU-04: Login with wrong password returns 401 ──────────────────────
    // KNOWN FAILING until GlobalExceptionHandler gets an AuthenticationException -> 401
    // mapping (currently falls through to the generic handler and returns 500).
    // Left asserting the CORRECT expected behavior on purpose — do not change this to 500.
    @Test
    @DisplayName("TC-AU-04: Login with invalid credentials returns 401 Unauthorized")
    void login_withInvalidCredentials_returnsUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest("jane@example.com", "WrongPassword!");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new org.springframework.security.authentication
                        .BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── TC-AU-05: Forgot password always returns success (no email leak) ──────
    @Test
    @DisplayName("TC-AU-05: Forgot password never reveals whether email exists")
    void forgotPassword_alwaysReturnsSuccess() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("nonexistent@example.com");

        doNothing().when(authService).forgotPassword(anyString());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "If an account exists with that email, a reset link has been sent."));
    }

    // ── TC-AU-06: Google auth — new user needs email verification ────────────
    // AuthController treats a null token in the AuthResponse as "new user, not verified yet"
    // and returns a message instead of data — confirmed in the real controller.
    @Test
    @DisplayName("TC-AU-06: Google auth for a brand-new user returns verification message, no token")
    void googleAuth_newUser_returnsVerificationMessage() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("mock-google-id-token");

        when(authService.googleAuth(any(GoogleAuthRequest.class)))
                .thenReturn(new AuthResponse(null, null));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "Please check your email to verify your account."));
    }

    // ── TC-AU-07: Google auth — existing/verified user logs straight in ──────
    @Test
    @DisplayName("TC-AU-07: Google auth for an existing verified user returns JWT token")
    void googleAuth_existingUser_returnsToken() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("mock-google-id-token");

        when(authService.googleAuth(any(GoogleAuthRequest.class)))
                .thenReturn(new AuthResponse("mock.jwt.token", "CUSTOMER"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("mock.jwt.token"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
    }

    // ── TC-AU-08: Email verification via token query param ───────────────────
    @Test
    @DisplayName("TC-AU-08: Verifying email with a valid token returns success message")
    void verifyEmail_withValidToken_returnsSuccess() throws Exception {
        doNothing().when(authService).verifyEmail(anyString());

        mockMvc.perform(get("/api/auth/verify")
                        .param("token", "mock-verification-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "Email verified successfully. You can now log in."));
    }

    // ── TC-AU-09: Resend verification email ───────────────────────────────────
    @Test
    @DisplayName("TC-AU-09: Resend verification returns success message")
    void resendVerification_returnsSuccess() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest("jane@example.com");

        doNothing().when(authService).resendVerification(eq("jane@example.com"));

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "Verification email sent. Please check your inbox."));
    }

    // ── TC-AU-10: Reset password with a valid token ───────────────────────────
    @Test
    @DisplayName("TC-AU-10: Reset password with valid token returns success message")
    void resetPassword_withValidToken_returnsSuccess() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest(
                "mock-reset-token", "NewSecurePass123!");

        doNothing().when(authService).resetPassword(eq("mock-reset-token"), eq("NewSecurePass123!"));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "Password reset successfully. You can now log in."));
    }

    // ── TC-AU-11: Reset password with a too-short new password returns 400
    // ResetPasswordRequest.newPassword has @Size(min = 8)
    @Test
    @DisplayName("TC-AU-11: Reset password with a new password under 8 characters returns 400")
    void resetPassword_withShortPassword_returnsBadRequest() throws Exception {
        String invalidJson = """
                { "token": "mock-reset-token", "newPassword": "short" }
                """;

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}