package com.blanchebridal.backend.order.controller;

import com.blanchebridal.backend.order.dto.req.*;
import com.blanchebridal.backend.order.dto.res.ProductionStageRecordResponse;
import com.blanchebridal.backend.order.service.ProductionStageRecordService;
import com.blanchebridal.backend.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProductionStageRecordController {

    private final ProductionStageRecordService productionService;

    @PostMapping("/api/admin/production/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductionStageRecordResponse> createRecord(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateProductionRecordRequest req,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(productionService.createRecord(orderId, req, admin.getId()));
    }

    @PutMapping("/api/admin/production/{orderId}/stage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductionStageRecordResponse> updateStageDirect(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateStageRequest req,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(productionService.updateStageDirect(orderId, req, admin.getId()));
    }

    @PostMapping("/api/employee/production/{orderId}/propose")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('EMPLOYEE') and @productionSecurity.isAssignedEmployee(#orderId, authentication))")
    public ResponseEntity<ProductionStageRecordResponse> proposeStage(
            @PathVariable UUID orderId,
            @Valid @RequestBody ProposeStageRequest req,
            @AuthenticationPrincipal User employee) {
        return ResponseEntity.ok(productionService.proposeStage(orderId, req, employee.getId()));
    }

    @PostMapping("/api/admin/production/{orderId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductionStageRecordResponse> approve(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(productionService.approve(orderId, admin.getId()));
    }

    @PostMapping("/api/admin/production/{orderId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductionStageRecordResponse> reject(
            @PathVariable UUID orderId,
            @Valid @RequestBody RejectProductionRequest req,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(productionService.reject(orderId, req, admin.getId()));
    }

    @PutMapping("/api/admin/production/{orderId}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductionStageRecordResponse> assignEmployee(
            @PathVariable UUID orderId,
            @Valid @RequestBody AssignEmployeeRequest req,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(productionService.assignEmployee(orderId, req, admin.getId()));
    }

    @GetMapping("/api/orders/{orderId}/production")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductionStageRecordResponse> getForCustomer(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User customer) {
        return productionService.getForCustomer(orderId, customer)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}