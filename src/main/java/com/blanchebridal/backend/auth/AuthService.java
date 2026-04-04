package com.blanchebridal.backend.auth;

import com.blanchebridal.backend.auth.dto.res.AuthResponse;
import com.blanchebridal.backend.auth.dto.req.GoogleAuthRequest;
import com.blanchebridal.backend.auth.dto.req.LoginRequest;
import com.blanchebridal.backend.auth.dto.req.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse googleAuth(GoogleAuthRequest request);
}