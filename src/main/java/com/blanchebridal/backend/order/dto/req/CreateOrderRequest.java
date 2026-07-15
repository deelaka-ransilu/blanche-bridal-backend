package com.blanchebridal.backend.order.dto.req;

import com.blanchebridal.backend.order.entity.DiscountType;
import com.blanchebridal.backend.order.entity.OrderMode;
import com.blanchebridal.backend.payment.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreateOrderRequest {

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    private String notes;

    // "DELIVERY" or "PICKUP" (case-insensitive). Defaults to DELIVERY if
    // omitted — see isDeliveryAddressValid() below, which requires
    // deliveryAddress whenever fulfillmentMethod isn't PICKUP.
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

    // Staff-only: applies a discount to the order total (FR-OM-11). Ignored/rejected
    // if the caller is a CUSTOMER — enforced in OrderServiceImpl, not here, since
    // the DTO layer doesn't know the caller's role.
    private DiscountType discountType;
    private BigDecimal discountValue;
    private String discountReason;

    @AssertTrue(message = "CARD payment is not yet supported — use PAYHERE or CASH")
    private boolean isPaymentMethodValid() {
        return paymentMethod == null || paymentMethod != PaymentMethod.CARD;
    }

    @AssertTrue(message = "discountValue is required when discountType is set")
    private boolean isDiscountValid() {
        return discountType == null || discountValue != null;
    }

    // Pickup orders don't need an address; everything else (including a
    // missing/blank fulfillmentMethod, which defaults to delivery behavior)
    // does. Without this, a client could submit "DELIVERY" with no address
    // and the order would be created anyway — the service layer just stores
    // whatever it's given.
    @AssertTrue(message = "deliveryAddress is required when fulfillmentMethod is DELIVERY")
    private boolean isDeliveryAddressValid() {
        if ("PICKUP".equalsIgnoreCase(fulfillmentMethod)) {
            return true;
        }
        return deliveryAddress != null && !deliveryAddress.isBlank();
    }
}