package com.blanchebridal.backend.order.dto.req;

import com.blanchebridal.backend.order.entity.ProductionStage;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter
public class CreateProductionRecordRequest {
    @NotNull
    private ProductionStage initialStage;
    private UUID assignedEmployeeId; // optional at creation
    private String notes;
    // getters/setters
}
