package com.blanchebridal.backend.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DiscountReportItem {
    private String month;                    // "2026-07"

    private long fixedDiscountOrderCount;
    private BigDecimal totalFixedDiscountAmount;   // sum of discountValue where type=FIXED

    private long percentageDiscountOrderCount;
    private BigDecimal averagePercentageDiscount;  // avg of discountValue where type=PERCENTAGE
}