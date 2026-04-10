package com.blanchebridal.backend.product.dto.res;

import com.blanchebridal.backend.product.entity.ProductType;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        String name,
        String slug,
        ProductType type,
        BigDecimal rentalPrice,
        BigDecimal purchasePrice,
        Integer stock,
        Boolean isAvailable,
        String firstImageUrl,
        Double averageRating,
        CategoryInfo category
) {
    public record CategoryInfo(UUID id, String name) {}
}