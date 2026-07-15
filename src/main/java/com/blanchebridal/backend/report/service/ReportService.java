package com.blanchebridal.backend.report.service;

import com.blanchebridal.backend.report.dto.DiscountReportItem;
import com.blanchebridal.backend.report.dto.RefundReportItem;
import com.blanchebridal.backend.report.dto.RevenueReportItem;
import com.blanchebridal.backend.report.dto.SummaryReport;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {
    List<RevenueReportItem> getRevenueReport(LocalDate from, LocalDate to);
    List<RefundReportItem> getRefundReport(LocalDate from, LocalDate to);
    List<DiscountReportItem> getDiscountReport(LocalDate from, LocalDate to);
    SummaryReport getSummary(LocalDate from, LocalDate to);
}