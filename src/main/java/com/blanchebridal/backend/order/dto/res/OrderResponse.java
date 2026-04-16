package com.blanchebridal.backend.order.dto.res;

import com.blanchebridal.backend.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrderResponse {
    private UUID id;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String notes;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Customer snapshot — included so admin views don't need a separate call
    // These are null-safe: user is SET NULL on delete
    private String customerEmail;
    private String customerFirstName;
    private String customerLastName;
}