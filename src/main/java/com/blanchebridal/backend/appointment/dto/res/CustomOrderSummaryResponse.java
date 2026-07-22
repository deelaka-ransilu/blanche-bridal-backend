package com.blanchebridal.backend.appointment.dto.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// Slim row DTO for the /admin/orders "Custom" tab list — deliberately not
// the full CustomDesignRequestResponse (no reference images, style
// preferences, etc). Just enough to render a table row and link into
// /admin/custom-orders/[id] for the full detail view.
@Data
@Builder
public class CustomOrderSummaryResponse {

    private UUID id; // CustomDesignRequest id

    private String customerName;
    private String customerEmail;

    private LocalDate occasionDate;

    private UUID firstPaymentOrderId;
    private UUID secondPaymentOrderId; // null until pickup

    private String firstPaymentStatus; // Order.status of firstPaymentOrder, as string

    private String currentProductionStage; // nullable — null if no ProductionStageRecord exists yet

    private LocalDateTime createdAt;
}