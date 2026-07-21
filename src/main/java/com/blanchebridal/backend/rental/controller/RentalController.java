package com.blanchebridal.backend.rental.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.rental.dto.req.*;
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

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
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

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
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

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
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

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> cancelRental(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = extractUserId(authHeader);
        String role = extractRole();

        log.info("[Rental] Cancel request — rental: {}, user: {}", id, userId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", rentalService.cancelRental(id, userId, role)
        ));
    }

    // CUSTOMER — self-service rental booking. Now books the FITTING
    // appointment (not pickup) and pays 50% of the rental fee at booking.
    @PostMapping("/book")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> bookRental(
            @Valid @RequestBody RentalBookingRequest request,
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = extractUserId(authHeader);

        log.info("[Rental] Booking request — customer: {}, product: {}, start: {}, end: {}, fitting: {} {}",
                userId, request.getProductId(), request.getRentalStart(),
                request.getRentalEnd(), request.getFittingDate(), request.getFittingTimeSlot());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", rentalService.bookRental(request, userId)
        ));
    }

    // ADMIN  confirm handover at pickup: creates the second
    // payment (remaining 50% rental fee + security deposit).
    @PostMapping("/{id}/handover")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> confirmHandover(
            @PathVariable UUID id,
            @Valid @RequestBody HandoverRequest request,
            @RequestHeader("Authorization") String authHeader) {

        UUID callerId = extractUserId(authHeader);
        String role = extractRole();

        log.info("[Rental] Handover confirmation request — rental: {}, caller: {}", id, callerId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", rentalService.confirmHandover(id, request, callerId, role)
        ));
    }

    // ADMIN — mark as returned, now with damage/late-fee inputs
    @PutMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> markReturned(
            @PathVariable UUID id,
            @Valid @RequestBody MarkReturnedRequest request) {

        log.info("[Rental] Mark returned — rental: {}, return date: {}, damaged: {}",
                id, request.getReturnDate(), request.isDamaged());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", rentalService.markReturned(id, request)
        ));
    }

    @PutMapping("/{id}/balance")
    @PreAuthorize("hasAnyRole('ADMIN')")
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

    @GetMapping("/rentable-products")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRentableProducts() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", rentalService.getRentableProducts()
        ));
    }

    @PostMapping("/walk-in")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createRentalBooking(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateRentalBookingRequest request) {

        UUID callerId = extractUserId(authHeader);
        String role = extractRole();
        log.info("[Rental] Walk-in booking request — caller: {}, role: {}, product: {}",
                callerId, role, request.getProductId());
        return ResponseEntity.ok(Map.of("success", true,
                "data", rentalService.createRentalBooking(request, callerId, role)));
    }

    @PutMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateNotes(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRentalNotesRequest request) {

        log.info("[Rental] Notes update — rental: {}", id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", rentalService.updateNotes(id, request)
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