package com.blanchebridal.backend.rental.dto.req;

import com.blanchebridal.backend.payment.entity.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class RentalBookingRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Rental start date is required")
    private LocalDate rentalStart;

    @NotNull(message = "Rental end date is required")
    private LocalDate rentalEnd;

    // Replaces the old single "timeSlot" (pickup slot) — pickup itself no
    // longer needs a slot, only the fitting does.
    @NotNull(message = "Fitting date is required")
    private LocalDate fittingDate;

    @NotNull(message = "Fitting time slot is required")
    private String fittingTimeSlot;

    // Re-added — rentals are no longer cash-only. The 50% fitting payment can
    // be cash or PayHere. CARD is rejected below, same convention as
    // CreateRentalBookingRequest.
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private String size;

    @AssertTrue(message = "rentalEnd must be after rentalStart")
    private boolean isDateRangeValid() {
        return rentalStart == null || rentalEnd == null || rentalEnd.isAfter(rentalStart);
    }

    @AssertTrue(message = "CARD payment is not yet supported — use PAYHERE or CASH")
    private boolean isPaymentMethodValid() {
        return paymentMethod == null || paymentMethod != PaymentMethod.CARD;
    }
}