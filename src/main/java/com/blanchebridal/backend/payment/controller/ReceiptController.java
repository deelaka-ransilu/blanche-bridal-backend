package com.blanchebridal.backend.payment.controller;

import com.blanchebridal.backend.payment.dto.res.ReceiptResponse;
import com.blanchebridal.backend.payment.service.ReceiptService;
import com.blanchebridal.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> getMyReceipts(
            @AuthenticationPrincipal User user) {

        UUID userId = user.getId();
        List<ReceiptResponse> receipts = receiptService.getMyReceipts(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", receipts));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getAllReceipts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ReceiptResponse> result = receiptService.getAllReceipts(
                PageRequest.of(page, size, Sort.by("issuedAt").descending()));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.getContent(),
                "pagination", Map.of(
                        "page", result.getNumber(),
                        "size", result.getSize(),
                        "total", result.getTotalElements(),
                        "totalPages", result.getTotalPages()
                )));
    }

    // GET /api/receipts/{id}/pdf — legacy: returns Cloudinary URL for
    // receipts generated before the DB-storage switch. Will return null
    // pdfUrl for anything generated after; prefer /download going forward.
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getReceiptPdfUrl(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        UUID requestingUserId = user.getId();
        String role = user.getRole().name();
        log.info("[Receipt] PDF access — receipt: {}, user: {}, role: {}",
                id, requestingUserId, role);
        String pdfUrl = receiptService.getReceiptPdfUrl(id, requestingUserId, role);
        return ResponseEntity.ok(Map.of("success", true,
                "data", Map.of("pdfUrl", pdfUrl)));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EMPLOYEE')")
    public ResponseEntity<byte[]> downloadReceiptPdf(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        UUID requestingUserId = user.getId();
        String role = user.getRole().name();

        byte[] pdfBytes = receiptService.downloadReceiptPdf(id, requestingUserId, role);
        String filename = receiptService.getReceiptFilename(id, requestingUserId, role);

        log.info("[Receipt] PDF download — receipt: {}, user: {}, role: {}", id, requestingUserId, role);

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }

    @GetMapping("/by-order/{orderId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getReceiptByOrderId(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User user) {

        UUID requestingUserId = user.getId();
        String role = user.getRole().name();
        ReceiptResponse receipt = receiptService.getReceiptByOrderId(orderId, requestingUserId, role);
        return ResponseEntity.ok(Map.of("success", true, "data", receipt));
    }
}