package com.blanchebridal.backend.product.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        UUID parentId,
        String parentName,
        LocalDateTime createdAt
) {}
