package com.blanchebridal.backend.payment.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.payment.dto.req.BankTransferProofRequest;
import com.blanchebridal.backend.payment.dto.req.InitiatePaymentRequest;
import com.blanchebridal.backend.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final JwtUtil jwtUtil;

    // CUSTOMER — generate PayHere hash + return all form fields
    @PostMapping("/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> initiatePayment(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody InitiatePaymentRequest request) {

        UUID userId = extractUserId(authHeader);
        log.info("[Payment] Initiate request — order: {}, user: {}",
                request.getOrderId(), userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", paymentService.initiatePayment(request.getOrderId(), userId)
        ));
    }

    /**
     * PayHere webhook — MUST be public (no JWT).
     * PayHere sends application/x-www-form-urlencoded, not JSON.
     * MUST always return HTTP 200 — PayHere retries on any other status.
     */
    @PostMapping(value = "/notify", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<String> handleWebhook(
            @RequestParam Map<String, String> params) {

        log.info("[Payment] Webhook received — order: {}, status: {}",
                params.getOrDefault("order_id", "unknown"),
                params.getOrDefault("status_code", "unknown"));
        try {
            paymentService.handleWebhook(params);
        } catch (Exception e) {
            // Log but swallow — NEVER return non-200 to PayHere
            log.error("[Payment] Error processing webhook: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok("OK");
    }

    // CUSTOMER — poll payment status from /checkout/success page
    // ADMIN/EMPLOYEE — diagnostic view on the order detail page
    @GetMapping("/status/{orderId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(
            @PathVariable UUID orderId,
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = extractUserId(authHeader);
        String role = extractRole();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", paymentService.getPaymentStatus(orderId, userId, role)
        ));
    }

    @PostMapping("/{orderId}/confirm-cash")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> confirmCashPayment(@PathVariable UUID orderId) {
        log.info("[Payment] Confirming cash payment for order {}", orderId);
        return ResponseEntity.ok(Map.of("success", true, "data", paymentService.confirmCashPayment(orderId)));
    }

    // CUSTOMER — upload proof-of-transfer, creates PENDING BANK_TRANSFER payment
    @PostMapping("/{orderId}/bank-transfer-proof")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> submitBankTransferProof(
            @PathVariable UUID orderId,
            @Valid @RequestBody BankTransferProofRequest request,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        log.info("[Payment] Bank transfer proof submitted → order: {}, user: {}", orderId, userId);
        return ResponseEntity.ok(Map.of("success", true,
                "data", paymentService.recordBankTransferProof(orderId, request.url(), userId)));
    }

    // ADMIN — review proof, confirm bank transfer payment
    @PostMapping("/{orderId}/confirm-bank-transfer")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> confirmBankTransfer(@PathVariable UUID orderId) {
        log.info("[Payment] Confirm bank transfer → order: {}", orderId);
        return ResponseEntity.ok(Map.of("success", true,
                "data", paymentService.confirmBankTransferPayment(orderId)));
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return UUID.fromString(jwtUtil.extractUserId(authHeader.substring(7)));
    }

    private String extractRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("");
    }
}