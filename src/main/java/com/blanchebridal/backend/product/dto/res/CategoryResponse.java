package com.blanchebridal.backend.product.dto.res;

import com.blanchebridal.backend.product.entity.CategoryType;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        CategoryType type,
        UUID parentId,
        String parentName,
        LocalDateTime createdAt
) {}