package com.blanchebridal.backend.order.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.dto.req.ConfirmSecondPaymentRequest;
import com.blanchebridal.backend.order.service.CustomOrderService;
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
@RequestMapping("/api/custom-design-requests")
@RequiredArgsConstructor
public class CustomOrderController {

    private final CustomOrderService customOrderService;
    private final JwtUtil jwtUtil;

    // ADMIN — confirm second (final) payment at pickup, once production
    // has reached READY_FOR_PICKUP. Mirrors RentalController#confirmHandover.
    @PostMapping("/{id}/confirm-second-payment")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> confirmSecondPayment(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmSecondPaymentRequest request,
            @RequestHeader("Authorization") String authHeader) {

        UUID adminId = extractUserId(authHeader);
        String role = extractRole();

        log.info("[CustomOrder] Second payment confirmation request — design request: {}, admin: {}",
                id, adminId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", customOrderService.confirmSecondPayment(id, request, adminId, role)
        ));
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