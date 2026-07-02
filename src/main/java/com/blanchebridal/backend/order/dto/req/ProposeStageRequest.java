package com.blanchebridal.backend.order.dto.req;

import com.blanchebridal.backend.order.entity.ProductionStage;
import lombok.Data;

@Data
public class ProposeStageRequest {
    private ProductionStage pendingStage;
    private String notes;
}
