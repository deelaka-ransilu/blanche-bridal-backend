package com.blanchebridal.backend.user.dto.res;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CustomerProfileResponse(
        UUID id,
        UUID customerId,
        String adminNotes,
        List<String> designImageUrls,
        LocalDateTime updatedAt
) {}