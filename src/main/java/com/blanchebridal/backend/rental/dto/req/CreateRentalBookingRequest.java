package com.blanchebridal.backend.rental.dto.req;

import com.blanchebridal.backend.payment.entity.PaymentMethod;
import com.blanchebridal.backend.rental.entity.RentalBookingPath;
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

    // Admin picks explicitly per booking — ADVANCE (50/50 split) or
    // SAME_DAY (full payment, dress leaves today).
    @NotNull(message = "bookingPath is required")
    private RentalBookingPath bookingPath;

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