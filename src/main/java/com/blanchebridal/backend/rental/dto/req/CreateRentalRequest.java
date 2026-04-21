package com.blanchebridal.backend.rental.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateRentalRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Rental start date is required")
    private LocalDate rentalStart;

    @NotNull(message = "Rental end date is required")
    private LocalDate rentalEnd;

    private BigDecimal depositAmount;
    private String notes;
    private UUID orderId; // optional — link to an existing order
}