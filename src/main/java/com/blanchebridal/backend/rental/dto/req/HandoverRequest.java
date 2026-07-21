package com.blanchebridal.backend.rental.dto.req;

import com.blanchebridal.backend.payment.entity.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HandoverRequest {

    // How the second payment (remaining 50% rental fee + security deposit)
    // will be collected. Cash confirms immediately via the existing
    // confirm-cash flow; PayHere returns a payment-initiate response for the
    // frontend to redirect to, same pattern as order checkout.
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @AssertTrue(message = "CARD payment is not yet supported — use PAYHERE or CASH")
    private boolean isPaymentMethodValid() {
        return paymentMethod == null || paymentMethod != PaymentMethod.CARD;
    }
}