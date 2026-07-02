package com.blanchebridal.backend.order.dto.res;

import com.blanchebridal.backend.order.entity.ProductionStage;
import com.blanchebridal.backend.order.entity.ProductionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionStageRecordResponse {
    private UUID id;
    private UUID orderId;
    private ProductionStage currentStage;
    private ProductionStage pendingStage;
    private UUID proposedById;
    private ProductionStatus status;
    private UUID assignedEmployeeId;
    private UUID reviewedById;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}