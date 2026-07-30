package com.blanchebridal.backend.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RevenueReportItem {
    private String month;
    private BigDecimal totalRevenue;
    private long orderCount;
}