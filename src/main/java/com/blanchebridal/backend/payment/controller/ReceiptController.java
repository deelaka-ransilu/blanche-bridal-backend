package com.blanchebridal.backend.payment.controller;

import com.blanchebridal.backend.payment.dto.res.ReceiptResponse;
import com.blanchebridal.backend.payment.service.ReceiptService;
import com.blanchebridal.backend.user.User;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    // GET /api/receipts/my — customer's own receipts
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> getMyReceipts(
            @AuthenticationPrincipal User user) {

        UUID userId = user.getId();
        List<ReceiptResponse> receipts = receiptService.getMyReceipts(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", receipts));
    }

    // GET /api/receipts — all receipts (admin/employee)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'SUPERADMIN')")
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

    // GET /api/receipts/{id}/pdf — returns Cloudinary URL
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EMPLOYEE', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> getReceiptPdfUrl(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {

        UUID requestingUserId = user.getId();
        String role = user.getRole().name();

        String pdfUrl = receiptService.getReceiptPdfUrl(id, requestingUserId, role);
        return ResponseEntity.ok(Map.of("success", true,
                "data", Map.of("pdfUrl", pdfUrl)));
    }
}