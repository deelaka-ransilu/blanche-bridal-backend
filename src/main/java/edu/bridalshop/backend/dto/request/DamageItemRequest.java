package edu.bridalshop.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DamageItemRequest(

        @NotBlank(message = "Damage description is required")
        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @NotNull(message = "Estimated cost is required")
        @DecimalMin(value = "0.0", message = "Cost cannot be negative")
        BigDecimal estimatedCost
) {}