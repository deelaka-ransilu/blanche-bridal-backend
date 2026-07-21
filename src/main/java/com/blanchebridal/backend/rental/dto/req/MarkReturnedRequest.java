package com.blanchebridal.backend.rental.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MarkReturnedRequest {

    @NotNull(message = "Return date is required")
    private LocalDate returnDate;

    // Whether the dress came back damaged. Late fee is always computed
    // server-side from rentalEnd vs returnDate — never submitted by the admin.
    private boolean damaged;

    // Only meaningful when damaged = true. Null/zero if not damaged.
    private BigDecimal damageCost;
}