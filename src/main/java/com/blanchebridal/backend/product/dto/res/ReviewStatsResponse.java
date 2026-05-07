package com.blanchebridal.backend.product.dto.res;

public record ReviewStatsResponse(
        Double averageRating,
        Long totalReviews,
        Long pendingReviews
) {}