package com.blanchebridal.backend.payment.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class InitiatePaymentRequest {

    @NotNull(message = "Order ID is required")
    private UUID orderId;
}