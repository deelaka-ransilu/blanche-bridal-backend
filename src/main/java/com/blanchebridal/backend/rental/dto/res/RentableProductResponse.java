package com.blanchebridal.backend.rental.dto.res;

import com.blanchebridal.backend.product.entity.ProductType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentableProductResponse {
    private UUID id;
    private String name;
    private ProductType type;
    private BigDecimal rentalPrice;
    private BigDecimal rentalPricePerDay;
    private String categoryName;
    private String firstImageUrl;
}