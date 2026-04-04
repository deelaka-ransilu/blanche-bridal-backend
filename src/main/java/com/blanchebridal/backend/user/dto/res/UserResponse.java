package com.blanchebridal.backend.user.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String role,
        String firstName,
        String lastName,
        String phone,
        Boolean isActive,
        LocalDateTime createdAt
) {}