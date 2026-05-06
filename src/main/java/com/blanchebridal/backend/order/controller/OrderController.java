package com.blanchebridal.backend.order.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.dto.req.CreateOrderRequest;
import com.blanchebridal.backend.order.dto.req.UpdateOrderStatusRequest;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final JwtUtil jwtUtil;

    // ADMIN + EMPLOYEE — all orders with optional status filter
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = orderService.getAllOrders(status, pageable);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.getContent(),
                "pagination", Map.of(
                        "page", result.getNumber(),
                        "size", result.getSize(),
                        "total", result.getTotalElements(),
                        "totalPages", result.getTotalPages()
                )
        ));
    }

    // CUSTOMER — own orders only
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> getMyOrders(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = extractUserId(authHeader);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = orderService.getMyOrders(userId, pageable);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.getContent(),
                "pagination", Map.of(
                        "page", result.getNumber(),
                        "size", result.getSize(),
                        "total", result.getTotalElements(),
                        "totalPages", result.getTotalPages()
                )
        ));
    }

    // CUSTOMER (own order only) + ADMIN + EMPLOYEE
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getOrderById(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = extractUserId(authHeader);
        String role = extractRole();
        return ResponseEntity.ok(Map.of("success", true,
                "data", orderService.getOrderById(id, userId, role)));
    }

    // CUSTOMER — create an order
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateOrderRequest request) {

        UUID userId = extractUserId(authHeader);
        log.info("[Order] Create request — user: {}, items: {}",
                userId, request.getItems() != null ? request.getItems().size() : 0);
        return ResponseEntity.ok(Map.of("success", true,
                "data", orderService.createOrder(request, userId)));
    }

    // ADMIN — update order status
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> updateOrderStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {

        log.info("[Order] Status update request — order: {}, new status: {}",
                id, request.getStatus());
        return ResponseEntity.ok(Map.of("success", true,
                "data", orderService.updateOrderStatus(id, request.getStatus())));
    }

    // CUSTOMER — cancel their own PENDING order (called from checkout/cancel page)
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = extractUserId(authHeader);
        log.info("[Order] Cancel request — order: {}, user: {}", id, userId);
        orderService.cancelOrder(id, userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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