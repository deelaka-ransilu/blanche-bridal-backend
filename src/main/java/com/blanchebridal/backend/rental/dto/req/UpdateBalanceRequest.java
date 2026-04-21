package com.blanchebridal.backend.rental.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateBalanceRequest {

    @NotNull(message = "Balance due is required")
    private BigDecimal balanceDue;
}