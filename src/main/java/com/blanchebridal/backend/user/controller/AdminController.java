package com.blanchebridal.backend.user.controller;

import com.blanchebridal.backend.user.dto.req.CreateUserRequest;
import com.blanchebridal.backend.user.dto.req.CreateWalkInCustomerRequest;
import com.blanchebridal.backend.user.dto.req.MeasurementsRequest;
import com.blanchebridal.backend.user.dto.req.UpdateCustomerProfileRequest;
import com.blanchebridal.backend.user.dto.res.CustomerDetailResponse;
import com.blanchebridal.backend.user.dto.res.MeasurementsResponse;
import com.blanchebridal.backend.user.dto.res.UserResponse;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
public class AdminController {

    private final AdminService adminService;

    // ── Employees ──────────────────────────────────────────────────────────

    @GetMapping("/employees")
    public ResponseEntity<Map<String, Object>> listEmployees() {
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.listEmployees()));
    }

    @PostMapping("/employees")
    public ResponseEntity<Map<String, Object>> createEmployee(
            @Valid @RequestBody CreateUserRequest request) {
        log.info("[Admin] Create employee → email: {}", request.email());
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.createEmployee(request)));
    }

    @PutMapping("/employees/{employeeId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.deactivateEmployee(employeeId)));
    }

    @PutMapping("/employees/{employeeId}/activate")
    public ResponseEntity<Map<String, Object>> activateEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.activateEmployee(employeeId)));
    }

    // ── Customers — list / activate / deactivate ───────────────────────────

    @GetMapping("/customers")
    public ResponseEntity<Map<String, Object>> listCustomers() {
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.listCustomers()));
    }

    @GetMapping("/customers/{customerId}")
    public ResponseEntity<Map<String, Object>> getCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.getCustomer(customerId)));
    }

    @PutMapping("/customers/{customerId}/activate")
    public ResponseEntity<Map<String, Object>> activateCustomer(@PathVariable UUID customerId) {
        log.info("[Admin] Activate customer → id: {}", customerId);
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.activateCustomer(customerId)));
    }

    @PutMapping("/customers/{customerId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateCustomer(@PathVariable UUID customerId) {
        log.info("[Admin] Deactivate customer → id: {}", customerId);
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.deactivateCustomer(customerId)));
    }

    // ── Customers — walk-in creation ───────────────────────────────────────

    @PostMapping("/customers")
    public ResponseEntity<Map<String, Object>> createWalkInCustomer(
            @Valid @RequestBody CreateWalkInCustomerRequest request) {
        log.info("[Admin] Create walk-in customer → email: {}", request.email());
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.createWalkInCustomer(request)));
    }

    // ── Customers — detail ─────────────────────────────────────────────────

    @GetMapping("/customers/{customerId}/detail")
    public ResponseEntity<Map<String, Object>> getCustomerDetail(@PathVariable UUID customerId) {
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.getCustomerDetail(customerId)));
    }

    // ── Customers — profile (notes + design images) ───────────────────────

    @PutMapping("/customers/{customerId}/profile")
    public ResponseEntity<Map<String, Object>> updateCustomerProfile(
            @PathVariable UUID customerId,
            @RequestBody UpdateCustomerProfileRequest request) {
        log.info("[Admin] Update profile for customer → id: {}", customerId);
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.updateCustomerProfile(customerId, request)));
    }

    // ── Customers — measurements ───────────────────────────────────────────

    @GetMapping("/customers/{customerId}/measurements")
    public ResponseEntity<Map<String, Object>> listMeasurements(@PathVariable UUID customerId) {
        return ResponseEntity.ok(Map.of("success", true, "data", adminService.listMeasurements(customerId)));
    }

    @PostMapping("/customers/{customerId}/measurements")
    public ResponseEntity<Map<String, Object>> addMeasurement(
            @PathVariable UUID customerId,
            @RequestBody MeasurementsRequest request,
            @AuthenticationPrincipal User currentUser) {
        // JwtFilter sets the full User entity as principal — cast and use getId() directly.
        // No employee list lookup needed; works for ADMIN and SUPERADMIN roles.
        UUID adminId = currentUser.getId();
        log.info("[Admin] Add measurement for customer → id: {}, recorded by: {}", customerId, adminId);
        MeasurementsResponse res = adminService.addMeasurement(customerId, request, adminId);
        return ResponseEntity.ok(Map.of("success", true, "data", res));
    }

    @PutMapping("/customers/{customerId}/measurements/{measurementId}")
    public ResponseEntity<Map<String, Object>> updateMeasurement(
            @PathVariable UUID customerId,
            @PathVariable UUID measurementId,
            @RequestBody MeasurementsRequest request) {
        log.info("[Admin] Update measurement {} for customer {}", measurementId, customerId);
        MeasurementsResponse res = adminService.updateMeasurement(customerId, measurementId, request);
        return ResponseEntity.ok(Map.of("success", true, "data", res));
    }
}