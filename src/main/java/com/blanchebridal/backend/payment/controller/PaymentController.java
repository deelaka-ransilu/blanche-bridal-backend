package com.blanchebridal.backend.payment.controller;

import com.blanchebridal.backend.auth.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.payment.dto.req.InitiatePaymentRequest;
import com.blanchebridal.backend.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final JwtUtil jwtUtil;

    // CUSTOMER — generate PayHere hash + return all form fields
    @PostMapping("/initiate")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> initiatePayment(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody InitiatePaymentRequest request) {

        UUID userId = extractUserId(authHeader);
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
    @PostMapping(value = "/notify",
            consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<String> handleWebhook(
            @RequestParam Map<String, String> params) {

        log.info("PayHere webhook received for order: {}",
                params.getOrDefault("order_id", "unknown"));
        try {
            paymentService.handleWebhook(params);
        } catch (Exception e) {
            // Log but swallow — NEVER return non-200 to PayHere
            log.error("Error processing PayHere webhook: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok("OK");
    }

    // CUSTOMER — poll payment status from /checkout/success page
    @GetMapping("/status/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(
            @PathVariable UUID orderId,
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", paymentService.getPaymentStatus(orderId, userId)
        ));
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return UUID.fromString(jwtUtil.extractUserId(authHeader.substring(7)));
    }
}