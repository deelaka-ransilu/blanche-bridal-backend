package com.blanchebridal.backend.rental.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MarkReturnedRequest {

    @NotNull(message = "Return date is required")
    private LocalDate returnDate;
}