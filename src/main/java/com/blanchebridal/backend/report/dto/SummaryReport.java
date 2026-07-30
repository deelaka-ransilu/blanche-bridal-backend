package com.blanchebridal.backend.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class SummaryReport {
    private LocalDate from;
    private LocalDate to;

    private BigDecimal totalRevenue;
    private long completedOrderCount;

    private BigDecimal totalRefunded;
    private long refundCount;

    private long discountedOrderCount;
    private BigDecimal totalFixedDiscountAmount;
    private long percentageDiscountOrderCount;
}