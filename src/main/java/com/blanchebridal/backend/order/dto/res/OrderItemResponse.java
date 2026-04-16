package com.blanchebridal.backend.order.dto.res;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class OrderItemResponse {
    private UUID productId;
    private String productName;
    private String productImage;
    private int quantity;
    private BigDecimal unitPrice;
    private String size;
    private BigDecimal subtotal;
}