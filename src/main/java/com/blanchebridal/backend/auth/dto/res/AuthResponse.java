package com.blanchebridal.backend.auth.dto.res;

public record AuthResponse(
        String token,
        String role
) {}