package com.blanchebridal.backend.product.dto.req;

import com.blanchebridal.backend.product.entity.ProductType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank String name,
        String description,
        @NotNull ProductType type,
        UUID categoryId,
        BigDecimal rentalPrice,
        BigDecimal purchasePrice,
        @NotNull @Min(0) Integer stock,
        List<String> sizes,
        List<String> imageUrls
) {}