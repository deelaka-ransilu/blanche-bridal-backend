package com.blanchebridal.backend.order.service;

import com.blanchebridal.backend.order.dto.req.ConfirmSecondPaymentRequest;
import com.blanchebridal.backend.order.dto.res.OrderResponse;

import java.util.UUID;

public interface CustomOrderService {

    /**
     * Confirms the second (final) payment for a custom order at pickup.
     * Precondition: the CustomDesignRequest's firstPaymentOrder must have an
     * associated ProductionStageRecord whose currentStage is READY_FOR_PICKUP.
     * Creates a new synthetic Order for the remaining 50% (skipped entirely
     * for FULL_UPFRONT quotes — this method should not be called for those).
     * Sets CustomDesignRequest.secondPaymentOrder. Does not touch
     * firstPaymentOrder.status or ProductionStageRecord — those stay as-is;
     * this only creates the payment order itself, ready for one of the
     * existing three payment-collection paths (cash/bank-transfer/PayHere)
     * to confirm against it, same as first payment.
     */
    OrderResponse confirmSecondPayment(UUID customDesignRequestId, ConfirmSecondPaymentRequest req, UUID adminId, String role);
}