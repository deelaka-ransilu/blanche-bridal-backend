package com.blanchebridal.backend.order.dto.req;

import com.blanchebridal.backend.order.entity.ProductionStage;
import lombok.Data;

@Data
public class UpdateStageRequest {
    private ProductionStage stage;
    private String notes;
}
