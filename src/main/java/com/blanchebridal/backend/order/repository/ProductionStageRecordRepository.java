package com.blanchebridal.backend.order.repository;

import com.blanchebridal.backend.order.entity.ProductionStageRecord;
import com.blanchebridal.backend.order.entity.ProductionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionStageRecordRepository extends JpaRepository<ProductionStageRecord, UUID> {

    Optional<ProductionStageRecord> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    List<ProductionStageRecord> findByAssignedEmployeeId(UUID employeeId);

    List<ProductionStageRecord> findByStatus(ProductionStatus status);

    List<ProductionStageRecord> findByAssignedEmployee_Id(UUID employeeId);
}