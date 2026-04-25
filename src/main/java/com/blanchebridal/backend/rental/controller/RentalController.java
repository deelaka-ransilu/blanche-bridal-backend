package com.blanchebridal.backend.rental.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.rental.dto.req.CreateRentalRequest;
import com.blanchebridal.backend.rental.dto.req.MarkReturnedRequest;
import com.blanchebridal.backend.rental.dto.req.UpdateBalanceRequest;
import com.blanchebridal.backend.rental.entity.RentalStatus;
import com.blanchebridal.backend.rental.service.RentalService;
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
@RequestMapping("/api/rentals")
@RequiredArgsConstructor
public class RentalController {

    private final RentalService rentalService;
    private final JwtUtil jwtUtil;

    // ADMIN + EMPLOYEE — all rentals, optional status filter
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getAllRentals(
            @RequestParam(required = false) RentalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = rentalService.getAllRentals(status, pageable);

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

    // CUSTOMER — own rentals only
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> getMyRentals(
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", rentalService.getMyRentals(userId)
        ));
    }

    // CUSTOMER (own) + ADMIN + EMPLOYEE
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getRentalById(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = extractUserId(authHeader);
        String role = extractRole();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", rentalService.getRentalById(id, userId, role)
        ));
    }

    // ADMIN + EMPLOYEE — create rental on behalf of customer
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> createRental(
            @Valid @RequestBody CreateRentalRequest request) {

        log.info("[Rental] Create request — product: {}, user: {}, start: {}, end: {}",
                request.getProductId(), request.getUserId(),
                request.getRentalStart(), request.getRentalEnd());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", rentalService.createRental(request)
        ));
    }

    // ADMIN + EMPLOYEE — mark as returned
    @PutMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> markReturned(
            @PathVariable UUID id,
            @Valid @RequestBody MarkReturnedRequest request) {

        log.info("[Rental] Mark returned — rental: {}, return date: {}",
                id, request.getReturnDate());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", rentalService.markReturned(id, request.getReturnDate())
        ));
    }

    // ADMIN only — update balance due
    @PutMapping("/{id}/balance")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> updateBalance(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBalanceRequest request) {

        log.info("[Rental] Balance update — rental: {}, new balance: {}",
                id, request.getBalanceDue());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", rentalService.updateBalance(id, request)
        ));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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