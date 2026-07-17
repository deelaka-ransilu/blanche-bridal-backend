package com.blanchebridal.backend.order.service.impl;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.dto.req.*;
import com.blanchebridal.backend.order.dto.res.ProductionStageRecordResponse;
import com.blanchebridal.backend.order.entity.*;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.order.repository.ProductionStageRecordRepository;
import com.blanchebridal.backend.order.service.ProductionStageRecordService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductionStageRecordServiceImpl implements ProductionStageRecordService {

    private final ProductionStageRecordRepository recordRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Override
    public ProductionStageRecordResponse createRecord(UUID orderId, CreateProductionRecordRequest req, UUID adminId) {
        if (recordRepository.existsByOrderId(orderId)) {
            throw new ConflictException("A production record already exists for this order");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        User assignedEmployee = null;
        if (req.getAssignedEmployeeId() != null) {
            assignedEmployee = resolveEmployee(req.getAssignedEmployeeId());
        }

        ProductionStageRecord record = ProductionStageRecord.builder()
                .order(order)
                .currentStage(req.getInitialStage())
                .status(ProductionStatus.NONE)
                .assignedEmployee(assignedEmployee)
                .notes(req.getNotes())
                .build();

        return toResponse(recordRepository.save(record));
    }

    @Override
    public ProductionStageRecordResponse updateStageDirect(UUID orderId, UpdateStageRequest req, UUID adminId) {
        ProductionStageRecord record = getRecordOrThrow(orderId);
        record.setCurrentStage(req.getStage());
        record.setPendingStage(null);
        record.setStatus(ProductionStatus.NONE);
        if (req.getNotes() != null) {
            record.setNotes(req.getNotes());
        }
        return toResponse(recordRepository.save(record));
    }

    @Override
    public ProductionStageRecordResponse proposeStage(UUID orderId, ProposeStageRequest req, UUID employeeId) {
        ProductionStageRecord record = getRecordOrThrow(orderId);

        if (record.getAssignedEmployee() == null || !record.getAssignedEmployee().getId().equals(employeeId)) {
            throw new UnauthorizedException("You are not assigned to this order's production record");
        }

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        record.setPendingStage(req.getPendingStage());
        record.setProposedBy(employee);
        record.setStatus(ProductionStatus.PENDING_APPROVAL);
        if (req.getNotes() != null) {
            record.setNotes(req.getNotes());
        }
        return toResponse(recordRepository.save(record));
    }

    @Override
    public ProductionStageRecordResponse approve(UUID orderId, UUID adminId) {
        ProductionStageRecord record = getRecordOrThrow(orderId);

        if (record.getStatus() != ProductionStatus.PENDING_APPROVAL || record.getPendingStage() == null) {
            throw new ConflictException("No pending stage change to approve");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        record.setCurrentStage(record.getPendingStage());
        record.setPendingStage(null);
        record.setStatus(ProductionStatus.APPROVED);
        record.setReviewedBy(admin);
        return toResponse(recordRepository.save(record));
    }

    @Override
    public ProductionStageRecordResponse reject(UUID orderId, RejectProductionRequest req, UUID adminId) {
        ProductionStageRecord record = getRecordOrThrow(orderId);

        if (record.getStatus() != ProductionStatus.PENDING_APPROVAL) {
            throw new ConflictException("No pending stage change to reject");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        record.setPendingStage(null);
        record.setStatus(ProductionStatus.REJECTED);
        record.setReviewedBy(admin);
        if (req.getNotes() != null) {
            record.setNotes(req.getNotes());
        }
        return toResponse(recordRepository.save(record));
    }

    @Override
    public ProductionStageRecordResponse assignEmployee(UUID orderId, AssignEmployeeRequest req, UUID adminId) {
        ProductionStageRecord record = getRecordOrThrow(orderId);
        User employee = resolveEmployee(req.getEmployeeId());
        record.setAssignedEmployee(employee);
        return toResponse(recordRepository.save(record));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductionStageRecordResponse> getForCustomer(UUID orderId, User requester) {
        return recordRepository.findByOrderId(orderId)
                .filter(record ->
                        requester.getRole() == UserRole.ADMIN
                                || requester.getRole() == UserRole.EMPLOYEE
                                || record.getOrder().getUser().getId().equals(requester.getId())
                )
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductionStageRecordResponse> getPendingApprovals() {
        return recordRepository.findByStatus(ProductionStatus.PENDING_APPROVAL)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // --- helpers ---

    private ProductionStageRecord getRecordOrThrow(UUID orderId) {
        return recordRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No production record exists for this order"));
    }

    private User resolveEmployee(UUID employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        if (employee.getRole() != UserRole.EMPLOYEE) {
            throw new ConflictException("Assigned user must have EMPLOYEE role");
        }
        return employee;
    }

    private ProductionStageRecordResponse toResponse(ProductionStageRecord r) {
        return ProductionStageRecordResponse.builder()
                .id(r.getId())
                .orderId(r.getOrder().getId())
                .currentStage(r.getCurrentStage())
                .pendingStage(r.getPendingStage())
                .proposedById(r.getProposedBy() != null ? r.getProposedBy().getId() : null)
                .status(r.getStatus())
                .assignedEmployeeId(r.getAssignedEmployee() != null ? r.getAssignedEmployee().getId() : null)
                .reviewedById(r.getReviewedBy() != null ? r.getReviewedBy().getId() : null)
                .notes(r.getNotes())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}