package com.blanchebridal.backend.order.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.dto.req.CreateCustomQuoteRequest;
import com.blanchebridal.backend.order.dto.req.RejectQuoteRequest;
import com.blanchebridal.backend.order.service.CustomQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CustomQuoteController {

    private final CustomQuoteService customQuoteService;
    private final JwtUtil jwtUtil;

    @PostMapping("/api/custom-design-requests/{id}/quotes")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createQuote(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCustomQuoteRequest request,
            @RequestHeader("Authorization") String authHeader) {
        UUID adminId = extractUserId(authHeader);
        log.info("[CustomQuote] Create → designRequest: {}, admin: {}", id, adminId);
        return ResponseEntity.ok(Map.of("success", true,
                "data", customQuoteService.createQuote(id, request, adminId)));
    }

    @GetMapping("/api/custom-design-requests/{id}/quotes/latest")
    public ResponseEntity<Map<String, Object>> getLatestQuote(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        UUID requesterId = extractUserId(authHeader);
        String role = extractRole(authHeader);
        return ResponseEntity.ok(Map.of("success", true,
                "data", customQuoteService.getLatestQuote(id, requesterId, role)));
    }

    @GetMapping("/api/custom-design-requests/{id}/quotes")
    public ResponseEntity<Map<String, Object>> getQuoteHistory(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        UUID requesterId = extractUserId(authHeader);
        String role = extractRole(authHeader);
        return ResponseEntity.ok(Map.of("success", true,
                "data", customQuoteService.getQuoteHistory(id, requesterId, role)));
    }

    @PostMapping("/api/quotes/{quoteId}/approve")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> approveQuote(
            @PathVariable UUID quoteId,
            @RequestHeader("Authorization") String authHeader) {
        UUID customerId = extractUserId(authHeader);
        log.info("[CustomQuote] Approve → quote: {}, customer: {}", quoteId, customerId);
        return ResponseEntity.ok(Map.of("success", true,
                "data", customQuoteService.approveQuote(quoteId, customerId)));
    }

    @PostMapping("/api/quotes/{quoteId}/reject")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> rejectQuote(
            @PathVariable UUID quoteId,
            @Valid @RequestBody RejectQuoteRequest request,
            @RequestHeader("Authorization") String authHeader) {
        UUID customerId = extractUserId(authHeader);
        log.info("[CustomQuote] Reject → quote: {}, customer: {}", quoteId, customerId);
        return ResponseEntity.ok(Map.of("success", true,
                "data", customQuoteService.rejectQuote(quoteId, customerId, request)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return UUID.fromString(jwtUtil.extractUserId(authHeader.substring(7)));
    }

    private String extractRole(String authHeader) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse(null);
    }
}