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

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    // Mirrors CreateOrderRequest's existing pattern — CARD is never valid here,
    // this is a customer self-service booking, not a staff-assisted one.
    @AssertTrue(message = "Card payment is not supported for rental bookings")
    private boolean isPaymentMethodValid() {
        return paymentMethod != PaymentMethod.CARD;
    }
}