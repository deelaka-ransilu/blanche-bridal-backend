package com.blanchebridal.backend.refund.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.refund.dto.BankDetailsResponse;
import com.blanchebridal.backend.refund.dto.RefundRequest;
import com.blanchebridal.backend.refund.dto.RefundResponse;
import com.blanchebridal.backend.refund.dto.SubmitBankDetailsRequest;
import com.blanchebridal.backend.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final JwtUtil jwtUtil;

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> refundOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) RefundRequest request,
            @RequestHeader("Authorization") String authHeader) {

        UUID adminId = extractUserId(authHeader);
        String reason = request != null ? request.getReason() : null;
        String proofImageUrl = request != null ? request.getProofImageUrl() : null;

        log.info("[Refund] Create request — order: {}, admin: {}", id, adminId);

        RefundResponse response = refundService.createRefund(id, reason, proofImageUrl, adminId);

        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }

    // Customer submits (or corrects) where the manual refund should be
    // transferred to. Only valid once the order is CANCELLED with a
    // COMPLETED payment and no Refund yet — enforced in the service.
    @PostMapping("/{id}/bank-details")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> submitBankDetails(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitBankDetailsRequest request,
            @RequestHeader("Authorization") String authHeader) {

        UUID customerId = extractUserId(authHeader);
        log.info("[Refund] Bank details submitted — order: {}, customer: {}", id, customerId);

        BankDetailsResponse response = refundService.submitBankDetails(id, customerId, request);

        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }

    // Admin-only read of the submitted bank details, so they know where to
    // send the manual transfer before uploading proof.
    @GetMapping("/{id}/bank-details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getBankDetails(@PathVariable UUID id) {
        BankDetailsResponse response = refundService.getBankDetails(id);
        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return UUID.fromString(jwtUtil.extractUserId(authHeader.substring(7)));
    }
}