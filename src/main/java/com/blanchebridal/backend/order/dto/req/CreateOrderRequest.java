package com.blanchebridal.backend.order.dto.req;

import com.blanchebridal.backend.order.entity.OrderMode;
import com.blanchebridal.backend.payment.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateOrderRequest {

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    private String notes;
    private String fulfillmentMethod;
    private String deliveryAddress;
    private String customerPhone;
    private OrderMode orderMode;

    // PAYHERE or CASH. Defaults to PAYHERE if omitted. CARD is rejected below —
    // not implemented anywhere in the payment flow yet.
    private PaymentMethod paymentMethod;

    // Staff-only: target customer when ADMIN/EMPLOYEE creates on behalf of a customer.
    // Ignored silently if the caller is a CUSTOMER.
    private UUID customerId;

    @AssertTrue(message = "CARD payment is not yet supported — use PAYHERE or CASH")
    private boolean isPaymentMethodValid() {
        return paymentMethod == null || paymentMethod != PaymentMethod.CARD;
    }
}