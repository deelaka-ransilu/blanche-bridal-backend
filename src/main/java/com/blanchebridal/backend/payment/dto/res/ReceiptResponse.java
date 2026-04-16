package com.blanchebridal.backend.payment.dto.res;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptResponse {
    private UUID id;
    private String receiptNumber;
    private String pdfUrl;
    private LocalDateTime issuedAt;
    private UUID orderId;
    private BigDecimal totalAmount;
}