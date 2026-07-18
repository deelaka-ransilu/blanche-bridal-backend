package com.blanchebridal.backend.rental.dto.req;

import com.blanchebridal.backend.payment.entity.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateRentalBookingRequest {

    @NotNull(message = "customerId is required")
    private UUID customerId;

    @NotNull(message = "productId is required")
    private UUID productId;

    @NotNull(message = "rentalStart is required")
    @FutureOrPresent(message = "rentalStart cannot be in the past")
    private LocalDate rentalStart;

    @NotNull(message = "rentalEnd is required")
    private LocalDate rentalEnd;

    // Same convention as CreateOrderRequest — CARD not supported anywhere in
    // the payment flow yet, so it's rejected below rather than at the enum
    // level.
    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    private String notes;

    @AssertTrue(message = "CARD payment is not yet supported — use PAYHERE or CASH")
    private boolean isPaymentMethodValid() {
        return paymentMethod == null || paymentMethod != PaymentMethod.CARD;
    }

    @AssertTrue(message = "rentalEnd must be after rentalStart")
    private boolean isDateRangeValid() {
        return rentalStart == null || rentalEnd == null || rentalEnd.isAfter(rentalStart);
    }
}