package com.blanchebridal.backend.refund.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.refund.dto.RefundRequest;
import com.blanchebridal.backend.refund.dto.RefundResponse;
import com.blanchebridal.backend.refund.service.RefundService;
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

        log.info("[Refund] Create request — order: {}, admin: {}", id, adminId);

        RefundResponse response = refundService.createRefund(id, reason, adminId);

        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return UUID.fromString(jwtUtil.extractUserId(authHeader.substring(7)));
    }
}