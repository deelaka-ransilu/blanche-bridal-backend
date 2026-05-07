package com.blanchebridal.backend.product.dto.res;

import com.blanchebridal.backend.product.entity.ReviewStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        Integer rating,
        String comment,
        ReviewStatus status,
        String reviewerName,
        LocalDateTime createdAt,
        UUID productId,
        String productName
) {}