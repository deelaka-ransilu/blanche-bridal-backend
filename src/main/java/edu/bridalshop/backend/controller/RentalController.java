package edu.bridalshop.backend.controller;

import edu.bridalshop.backend.dto.request.RentalCreateRequest;
import edu.bridalshop.backend.dto.request.RentalReturnRequest;
import edu.bridalshop.backend.dto.response.RentalResponse;
import org.springframework.security.core.userdetails.UserDetails;
import edu.bridalshop.backend.service.RentalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rentals")
@RequiredArgsConstructor
public class RentalController {

    private final RentalService rentalService;

    // -------------------------------------------------------------------------
    // GET /api/rentals --- ADMIN or EMPLOYEE
    // -------------------------------------------------------------------------
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<RentalResponse>> getAll() {
        return ResponseEntity.ok(rentalService.getAll());
    }

    // -------------------------------------------------------------------------
    // GET /api/rentals/overdue --- ADMIN or EMPLOYEE
    // -------------------------------------------------------------------------
    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<List<RentalResponse>> getOverdue() {
        return ResponseEntity.ok(rentalService.getOverdue());
    }

    // -------------------------------------------------------------------------
    // GET /api/rentals/{publicId} --- ADMIN or EMPLOYEE
    // -------------------------------------------------------------------------
    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<RentalResponse> getByPublicId(@PathVariable String publicId) {
        return ResponseEntity.ok(rentalService.getByPublicId(publicId));
    }

    // -------------------------------------------------------------------------
    // POST /api/rentals --- ADMIN or EMPLOYEE
    // -------------------------------------------------------------------------
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<RentalResponse> create(
            @Valid @RequestBody RentalCreateRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rentalService.create(request, principal.getUsername()));
    }

    // -------------------------------------------------------------------------
    // PUT /api/rentals/{publicId}/handover --- ADMIN or EMPLOYEE
    // Transitions: BOOKED → HANDED_OVER
    // -------------------------------------------------------------------------
    @PutMapping("/{publicId}/handover")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<RentalResponse> handover(@PathVariable String publicId) {
        return ResponseEntity.ok(rentalService.handover(publicId));
    }

    // -------------------------------------------------------------------------
    // PUT /api/rentals/{publicId}/return --- ADMIN or EMPLOYEE
    // Processes return, calculates financials, sets final status
    // -------------------------------------------------------------------------
    @PutMapping("/{publicId}/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<RentalResponse> processReturn(
            @PathVariable String publicId,
            @Valid @RequestBody RentalReturnRequest request) {
        return ResponseEntity.ok(rentalService.processReturn(publicId, request));
    }
}