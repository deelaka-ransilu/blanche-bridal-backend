package com.blanchebridal.backend.product.dto.req;

import com.blanchebridal.backend.product.entity.ProductType;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateProductRequest(
        String name,
        String description,
        ProductType type,
        UUID categoryId,
        BigDecimal rentalPrice,
        BigDecimal purchasePrice,
        @Min(0) Integer stock,
        List<String> sizes,
        List<String> imageUrls,
        Boolean isAvailable
) {}