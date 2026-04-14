package com.blanchebridal.backend.auth;

import com.blanchebridal.backend.auth.dto.res.AuthResponse;
import com.blanchebridal.backend.auth.dto.req.*;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse googleAuth(GoogleAuthRequest request);

    void verifyEmail(String token);
    void resendVerification(String email);
    void forgotPassword(String email);
    void resetPassword(String token, String newPassword);
}