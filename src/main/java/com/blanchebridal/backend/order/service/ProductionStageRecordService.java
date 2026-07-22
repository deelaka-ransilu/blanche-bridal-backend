package com.blanchebridal.backend.order.service;

import com.blanchebridal.backend.order.dto.req.*;
import com.blanchebridal.backend.order.dto.res.ProductionStageRecordResponse;
import com.blanchebridal.backend.user.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionStageRecordService {

    ProductionStageRecordResponse createRecord(UUID orderId, CreateProductionRecordRequest req, UUID adminId);

    ProductionStageRecordResponse updateStageDirect(UUID orderId, UpdateStageRequest req, UUID adminId);

    ProductionStageRecordResponse proposeStage(UUID orderId, ProposeStageRequest req, UUID employeeId);

    ProductionStageRecordResponse approve(UUID orderId, UUID adminId);

    ProductionStageRecordResponse reject(UUID orderId, RejectProductionRequest req, UUID adminId);

    ProductionStageRecordResponse assignEmployee(UUID orderId, AssignEmployeeRequest req, UUID adminId);

    Optional<ProductionStageRecordResponse> getForCustomer(UUID orderId, User requester);

    // Admin dashboard — all records currently awaiting admin approval
    // (i.e. an employee has proposed a stage change that hasn't been
    // approved or rejected yet).
    List<ProductionStageRecordResponse> getPendingApprovals();

    List<ProductionStageRecordResponse> getMyAssignedProductions(UUID employeeId);
}