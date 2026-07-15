package com.blanchebridal.backend.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

// For GET /api/admin/reports/summary
@Data
@Builder
public class SummaryReport {
    private LocalDate from;
    private LocalDate to;

    private BigDecimal totalRevenue;      // sum of Order.totalAmount, status=COMPLETED
    private long completedOrderCount;

    private BigDecimal totalRefunded;     // sum of Refund.amount
    private long refundCount;

    // See DiscountReportItem's comment -- same FIXED-vs-PERCENTAGE split
    // applies here, so this stays as counts + the FIXED currency sum only,
    // not a single blended "total discounted" figure.
    private long discountedOrderCount;
    private BigDecimal totalFixedDiscountAmount;
    private long percentageDiscountOrderCount;
}