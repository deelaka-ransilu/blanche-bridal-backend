package com.blanchebridal.backend.product.dto;

import com.blanchebridal.backend.product.entity.ProductType;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductFilters(
        ProductType type,
        UUID categoryId,
        String search,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean available
) {}