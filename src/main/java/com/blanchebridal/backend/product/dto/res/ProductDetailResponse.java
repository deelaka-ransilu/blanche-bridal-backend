package com.blanchebridal.backend.product.dto.res;

import com.blanchebridal.backend.product.entity.ProductType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProductDetailResponse(
        UUID id,
        String name,
        String slug,
        String description,
        ProductType type,
        BigDecimal rentalPrice,
        BigDecimal purchasePrice,
        Integer stock,
        Boolean isAvailable,
        List<String> sizes,
        List<ImageInfo> images,
        Double averageRating,
        ProductSummaryResponse.CategoryInfo category,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record ImageInfo(UUID id, String url, Integer displayOrder) {}
}