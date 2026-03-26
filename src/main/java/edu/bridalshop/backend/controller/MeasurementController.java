package edu.bridalshop.backend.controller;

import edu.bridalshop.backend.dto.request.MeasurementRequest;
import edu.bridalshop.backend.dto.response.MeasurementResponse;
import edu.bridalshop.backend.service.MeasurementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerPublicId}/measurements")
@RequiredArgsConstructor
public class MeasurementController {

    private final MeasurementService measurementService;

    // -------------------------------------------------------------------------
    // GET /api/customers/{customerPublicId}/measurements
    // ADMIN + EMPLOYEE: any customer | CUSTOMER: self only
    // -------------------------------------------------------------------------
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    public ResponseEntity<List<MeasurementResponse>> getAll(
            @PathVariable String customerPublicId,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(
                measurementService.getAll(customerPublicId, principal.getUsername()));
    }

    // -------------------------------------------------------------------------
    // GET /api/customers/{customerPublicId}/measurements/{measurementPublicId}
    // ADMIN + EMPLOYEE: any customer | CUSTOMER: self only
    // -------------------------------------------------------------------------
    @GetMapping("/{measurementPublicId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    public ResponseEntity<MeasurementResponse> getOne(
            @PathVariable String customerPublicId,
            @PathVariable String measurementPublicId,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(
                measurementService.getOne(customerPublicId, measurementPublicId, principal.getUsername()));
    }

    // -------------------------------------------------------------------------
    // POST /api/customers/{customerPublicId}/measurements
    // ADMIN only
    // -------------------------------------------------------------------------
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MeasurementResponse> create(
            @PathVariable String customerPublicId,
            @Valid @RequestBody MeasurementRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(measurementService.create(customerPublicId, request, principal.getUsername()));
    }

    // -------------------------------------------------------------------------
    // PUT /api/customers/{customerPublicId}/measurements/{measurementPublicId}
    // ADMIN only
    // -------------------------------------------------------------------------
    @PutMapping("/{measurementPublicId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MeasurementResponse> update(
            @PathVariable String customerPublicId,
            @PathVariable String measurementPublicId,
            @Valid @RequestBody MeasurementRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(
                measurementService.update(customerPublicId, measurementPublicId, request, principal.getUsername()));
    }
}