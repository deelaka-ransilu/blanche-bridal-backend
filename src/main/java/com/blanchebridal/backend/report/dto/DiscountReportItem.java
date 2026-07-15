package com.blanchebridal.backend.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// One row per calendar month, for GET /api/admin/reports/discounts
//
// FIXED and PERCENTAGE discounts are kept as separate fields rather than
// summed into one "total discount amount" -- Order has no stored pre-discount
// subtotal, so a PERCENTAGE discount's actual currency value can't be
// reconstructed here. discountValue for FIXED orders IS a currency amount
// (safe to sum); discountValue for PERCENTAGE orders is a raw percentage
// (only safe to average, never sum with currency).
@Data
@Builder
public class DiscountReportItem {
    private String month;                    // "2026-07"

    private long fixedDiscountOrderCount;
    private BigDecimal totalFixedDiscountAmount;   // sum of discountValue where type=FIXED

    private long percentageDiscountOrderCount;
    private BigDecimal averagePercentageDiscount;  // avg of discountValue where type=PERCENTAGE
}