package com.blanchebridal.backend.refund.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RefundResponse {
    private UUID id;
    private UUID orderId;
    private BigDecimal amount;
    private String reason;
    private String proofImageUrl;
    private UUID processedByAdminId;
    private LocalDateTime createdAt;
}