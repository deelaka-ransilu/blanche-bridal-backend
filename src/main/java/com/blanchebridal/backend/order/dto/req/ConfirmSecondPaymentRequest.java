package com.blanchebridal.backend.order.dto.req;

import com.blanchebridal.backend.payment.entity.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfirmSecondPaymentRequest {

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @AssertTrue(message = "CARD payment is not yet supported — use PAYHERE, CASH, or BANK_TRANSFER")
    private boolean isPaymentMethodValid() {
        return paymentMethod == null || paymentMethod != PaymentMethod.CARD;
    }
}