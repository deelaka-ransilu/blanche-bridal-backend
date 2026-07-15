package com.blanchebridal.backend.rental.dto.req;

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

    // paymentMethod removed — rentals are cash-only now (decided this session).
    // PaymentServiceImpl.confirmCashPayment() is still used by admin to mark
    // the linked synthetic order paid; PayHere/card are never used for rentals.
}