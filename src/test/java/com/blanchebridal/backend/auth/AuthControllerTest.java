package com.blanchebridal.backend.auth;

import com.blanchebridal.backend.auth.controller.AuthController;
import com.blanchebridal.backend.auth.dto.req.ForgotPasswordRequest;
import com.blanchebridal.backend.auth.dto.req.GoogleAuthRequest;
import com.blanchebridal.backend.auth.dto.req.LoginRequest;
import com.blanchebridal.backend.auth.dto.req.RegisterRequest;
import com.blanchebridal.backend.auth.dto.req.ResendVerificationRequest;
import com.blanchebridal.backend.auth.dto.req.ResetPasswordRequest;
import com.blanchebridal.backend.auth.dto.res.AuthResponse;
import com.blanchebridal.backend.auth.dto.res.RefreshResponse;
import com.blanchebridal.backend.auth.security.JwtFilter;
import com.blanchebridal.backend.auth.service.AuthService;
import com.blanchebridal.backend.config.security.SecurityConfig;
import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice tests for AuthController.
 *
 * Every endpoint on this controller is permitAll() in SecurityConfig (see
 * SecurityConfig.filterChain — /api/auth/register, /login, /google, /verify,
 * /resend-verification, /forgot-password, /reset-password, /refresh, /logout
 * are all explicitly listed). So unlike OrderControllerTest / PaymentControllerTest,
 * none of these requests need a Spring Security authentication() post-processor —
 * plain MockMvc calls are enough. JwtFilter is still mocked as a pass-through because
 * SecurityConfig's constructor requires a JwtFilter bean, and the real one needs
 * jwt.secret / jwt.expiration properties and a UserRepository that aren't available
 * in this @WebMvcTest slice.
 *
 * NOTE on the Google OAuth path (guide's "mock GoogleIdTokenVerifier if feasible"):
 * at this controller-slice level AuthService is mocked entirely, so
 * GoogleIdTokenVerifier — used inside AuthServiceImpl.googleAuth() — never actually
 * executes here; there is nothing to mock at this layer. Exercising the real Google-
 * token-verification logic would need a separate AuthServiceImpl-level unit test with
 * GoogleIdTokenVerifier mocked directly. That's out of scope for a controller slice
 * and is called out here explicitly rather than silently skipped.
 */
@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtFilter jwtFilter;

    @BeforeEach
    void makeJwtFilterPassThrough() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    // ─── A. Register ────────────────────────────────────────────────────────

    @Nested
    class Register {

        @Test
        @DisplayName("TC-AU-01: valid registration returns 200")
        void register_valid_returns200() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "new@customer.com", "password123", "New", "Customer", "0771234567");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(authService).register(any());
        }

        @Test
        @DisplayName("TC-AU-02: duplicate email is rejected with 409")
        void register_duplicateEmail_returns409() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "existing@customer.com", "password123", "New", "Customer", "0771234567");
            doThrow(new ConflictException("Email already in use")).when(authService).register(any());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Email already in use"));
        }

        @Test
        @DisplayName("TC-AU-03: invalid input (bad email, short password) returns 400")
        void register_invalidInput_returns400() throws Exception {
            String invalidJson = "{\"email\":\"not-an-email\",\"password\":\"123\","
                    + "\"firstName\":\"\",\"lastName\":\"User\",\"phone\":\"0771234567\"}";

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).register(any());
        }
    }

    // ─── B. Login ───────────────────────────────────────────────────────────

    @Nested
    class Login {

        @Test
        @DisplayName("TC-AU-04: valid credentials return a token and set the refresh cookie")
        void login_valid_returnsTokenAndSetsCookie() throws Exception {
            LoginRequest request = new LoginRequest("customer@test.com", "correct-password");
            when(authService.login(any())).thenReturn(
                    new AuthResponse("access-token-abc", "CUSTOMER", "raw-refresh-token"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("access-token-abc"))
                    .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                    .andExpect(cookie().value("refreshToken", "raw-refresh-token"))
                    .andExpect(cookie().httpOnly("refreshToken", true));
        }

        @Test
        @DisplayName("TC-AU-05: wrong password returns 401 with the generic message")
        void login_wrongPassword_returns401() throws Exception {
            LoginRequest request = new LoginRequest("customer@test.com", "wrong-password");
            when(authService.login(any()))
                    .thenThrow(new UnauthorizedException("Invalid email or password"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        @Test
        @DisplayName("TC-AU-06: non-existent email returns 401 with the SAME generic message as wrong password")
        void login_nonExistentEmail_returns401WithSameMessageAsWrongPassword() throws Exception {
            // Deliberate security behavior: AuthServiceImpl.login() throws the identical
            // message for "no such user" and "wrong password" so a caller can't use the
            // response to enumerate valid emails. This test locks in that ambiguity — it
            // does NOT assert a different message for this case.
            LoginRequest request = new LoginRequest("ghost@nowhere.com", "whatever");
            when(authService.login(any()))
                    .thenThrow(new UnauthorizedException("Invalid email or password"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        @Test
        @DisplayName("TC-AU-07: unverified account is rejected before login succeeds")
        void login_unverifiedAccount_returns401() throws Exception {
            LoginRequest request = new LoginRequest("unverified@customer.com", "password123");
            when(authService.login(any())).thenThrow(new UnauthorizedException(
                    "Please verify your email before logging in. Check your inbox."));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(
                            "Please verify your email before logging in. Check your inbox."));
        }

        @Test
        @DisplayName("TC-AU-08: invalid input (blank password) returns 400")
        void login_blankPassword_returns400() throws Exception {
            String invalidJson = "{\"email\":\"customer@test.com\",\"password\":\"\"}";

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).login(any());
        }
    }

    // ─── C. Google OAuth ────────────────────────────────────────────────────

    @Nested
    class GoogleAuth {

        @Test
        @DisplayName("TC-AU-09: new Google account returns 200 with a verification-pending message, no token/data")
        void googleAuth_newAccount_returnsPendingVerificationMessage() throws Exception {
            GoogleAuthRequest request = new GoogleAuthRequest("valid-google-id-token");
            when(authService.googleAuth(any())).thenReturn(new AuthResponse(null, null, null));

            mockMvc.perform(post("/api/auth/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("TC-AU-10: existing verified Google account returns a token and sets the refresh cookie")
        void googleAuth_existingAccount_returnsTokenAndSetsCookie() throws Exception {
            GoogleAuthRequest request = new GoogleAuthRequest("valid-google-id-token");
            when(authService.googleAuth(any())).thenReturn(
                    new AuthResponse("access-token-xyz", "CUSTOMER", "raw-refresh-token-2"));

            mockMvc.perform(post("/api/auth/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("access-token-xyz"))
                    .andExpect(cookie().value("refreshToken", "raw-refresh-token-2"));
        }

        @Test
        @DisplayName("TC-AU-11: invalid Google token returns 401")
        void googleAuth_invalidToken_returns401() throws Exception {
            GoogleAuthRequest request = new GoogleAuthRequest("garbage-token");
            when(authService.googleAuth(any()))
                    .thenThrow(new UnauthorizedException("Google authentication failed"));

            mockMvc.perform(post("/api/auth/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── D. Password reset ──────────────────────────────────────────────────

    @Nested
    class PasswordReset {

        @Test
        @DisplayName("TC-AU-12: forgot-password with a valid email returns 200")
        void forgotPassword_validEmail_returns200() throws Exception {
            ForgotPasswordRequest request = new ForgotPasswordRequest("customer@test.com");

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(authService).forgotPassword("customer@test.com");
        }

        @Test
        @DisplayName("TC-AU-13: reset-password with a too-short newPassword (<8 chars) returns 400")
        void resetPassword_tooShortPassword_returns400() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest("some-token", "short1");

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).resetPassword(any(), any());
        }

        @Test
        @DisplayName("TC-AU-14: reset-password with a valid 8+ char password and valid token succeeds")
        void resetPassword_validTokenAndPassword_returns200() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest("valid-token", "newpassword123");

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(authService).resetPassword("valid-token", "newpassword123");
        }

        @Test
        @DisplayName("TC-AU-15: reset-password with an expired token returns 401")
        void resetPassword_expiredToken_returns401() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest("expired-token", "newpassword123");
            doThrow(new UnauthorizedException("Reset link has expired. Please request a new one."))
                    .when(authService).resetPassword("expired-token", "newpassword123");

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-AU-16: reset-password with an unknown token returns 404")
        void resetPassword_unknownToken_returns404() throws Exception {
            ResetPasswordRequest request = new ResetPasswordRequest("unknown-token", "newpassword123");
            doThrow(new ResourceNotFoundException("Invalid or expired reset link"))
                    .when(authService).resetPassword("unknown-token", "newpassword123");

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── E. Refresh token flow ──────────────────────────────────────────────

    @Nested
    class Refresh {

        @Test
        @DisplayName("TC-AU-17: valid refresh cookie issues a new access token and rotates the cookie")
        void refresh_validCookie_issuesNewAccessTokenAndRotatesCookie() throws Exception {
            when(authService.refresh("old-raw-refresh-token"))
                    .thenReturn(new RefreshResponse("new-access-token", "new-raw-refresh-token"));

            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new Cookie("refreshToken", "old-raw-refresh-token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("new-access-token"))
                    .andExpect(cookie().value("refreshToken", "new-raw-refresh-token"));
        }

        @Test
        @DisplayName("TC-AU-18: missing refresh cookie is rejected with 401 without calling the service")
        void refresh_missingCookie_returns401() throws Exception {
            mockMvc.perform(post("/api/auth/refresh"))
                    .andExpect(status().isUnauthorized());

            verify(authService, never()).refresh(any());
        }

        @Test
        @DisplayName("TC-AU-19: expired/invalid refresh token is rejected with 401")
        void refresh_expiredToken_returns401() throws Exception {
            when(authService.refresh("stale-raw-refresh-token"))
                    .thenThrow(new UnauthorizedException("Refresh token expired. Please log in again."));

            mockMvc.perform(post("/api/auth/refresh")
                            .cookie(new Cookie("refreshToken", "stale-raw-refresh-token")))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── F. Verify / resend / logout ────────────────────────────────────────

    @Nested
    class MiscEndpoints {

        @Test
        @DisplayName("TC-AU-20: email verification with a valid token returns 200")
        void verifyEmail_validToken_returns200() throws Exception {
            mockMvc.perform(get("/api/auth/verify").param("token", "valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(authService).verifyEmail("valid-token");
        }

        @Test
        @DisplayName("TC-AU-21: email verification with an expired token returns 401")
        void verifyEmail_expiredToken_returns401() throws Exception {
            doThrow(new UnauthorizedException("Verification link has expired. Please request a new one."))
                    .when(authService).verifyEmail("expired-token");

            mockMvc.perform(get("/api/auth/verify").param("token", "expired-token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-AU-22: resend-verification for an already-active account returns 409")
        void resendVerification_alreadyActive_returns409() throws Exception {
            ResendVerificationRequest request = new ResendVerificationRequest("active@customer.com");
            doThrow(new ConflictException("This account is already verified"))
                    .when(authService).resendVerification("active@customer.com");

            mockMvc.perform(post("/api/auth/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("TC-AU-23: logout with a refresh cookie present revokes it and clears the cookie")
        void logout_withCookie_clearsCookie() throws Exception {
            mockMvc.perform(post("/api/auth/logout")
                            .cookie(new Cookie("refreshToken", "raw-token-to-revoke")))
                    .andExpect(status().isOk())
                    .andExpect(cookie().maxAge("refreshToken", 0));

            verify(authService).logout("raw-token-to-revoke");
        }

        @Test
        @DisplayName("TC-AU-24: logout with no refresh cookie still returns 200 without calling the service")
        void logout_noCookie_returns200WithoutCallingService() throws Exception {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk());

            verify(authService, never()).logout(any());
        }
    }
}