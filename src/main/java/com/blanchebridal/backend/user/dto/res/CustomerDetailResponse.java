package com.blanchebridal.backend.user.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CustomerDetailResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        @JsonProperty("active") Boolean isActive,
        LocalDateTime createdAt,
        // profile extras
        String adminNotes,
        List<String> designImageUrls,
        // measurement snapshots (newest first)
        List<MeasurementsResponse> measurements
) {}