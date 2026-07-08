package com.blanchebridal.backend.report.controller;

import com.blanchebridal.backend.report.dto.DiscountReportItem;
import com.blanchebridal.backend.report.dto.RefundReportItem;
import com.blanchebridal.backend.report.dto.RevenueReportItem;
import com.blanchebridal.backend.report.dto.SummaryReport;
import com.blanchebridal.backend.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// URL is under /api/admin/**, already covered by SecurityConfig's blanket
// hasRole("ADMIN") rule for that prefix -- no SecurityConfig change needed.
// @PreAuthorize kept anyway for consistency with RefundController's pattern
// of stacking both.
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;

    // from/to are optional; ReportServiceImpl defaults to a trailing-12-month
    // window when either is omitted (see DEFAULT_MONTHS_BACK).
    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> getRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<RevenueReportItem> data = reportService.getRevenueReport(from, to);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/refunds")
    public ResponseEntity<Map<String, Object>> getRefunds(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<RefundReportItem> data = reportService.getRefundReport(from, to);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/discounts")
    public ResponseEntity<Map<String, Object>> getDiscounts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<DiscountReportItem> data = reportService.getDiscountReport(from, to);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SummaryReport data = reportService.getSummary(from, to);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
}