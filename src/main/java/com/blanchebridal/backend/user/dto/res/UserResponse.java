package com.blanchebridal.backend.user.dto.res;

import com.blanchebridal.backend.user.entity.UserRole;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String role,
        String firstName,
        String lastName,
        String phone,
        String address,
        @JsonProperty("active") Boolean isActive,
        LocalDateTime createdAt
) {}