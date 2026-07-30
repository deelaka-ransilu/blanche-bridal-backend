package com.blanchebridal.backend.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RefundReportItem {
    private String month;        // "2026-07"
    private BigDecimal totalRefunded;
    private long refundCount;
}