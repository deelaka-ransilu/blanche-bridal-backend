package com.blanchebridal.backend.product.dto.req;

import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateProductRequest(
        String name,
        String description,
        UUID categoryId,
        BigDecimal rentalPrice,
        BigDecimal rentalPricePerDay,
        BigDecimal purchasePrice,
        @Min(0) Integer stock,
        List<String> sizes,
        List<ProductImageInput> images,
        Boolean isAvailable
) {}