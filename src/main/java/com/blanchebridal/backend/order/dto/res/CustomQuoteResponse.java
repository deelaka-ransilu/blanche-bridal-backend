package com.blanchebridal.backend.order.dto.res;

import com.blanchebridal.backend.order.entity.QuoteStatus;
import com.blanchebridal.backend.order.entity.SplitType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record CustomQuoteResponse(
        UUID id,
        UUID customDesignRequestId,
        int version,
        BigDecimal fabricAmount,
        BigDecimal laborAmount,
        BigDecimal embellishmentAmount,
        BigDecimal alterationsAmount,
        BigDecimal otherAmount,
        String otherNote,
        BigDecimal totalAmount,
        SplitType splitType,
        QuoteStatus status,
        String rejectionReason,
        LocalDateTime validUntil,
        LocalDateTime createdAt,
        boolean isExpired // computed at map-time, never stored
) {
}