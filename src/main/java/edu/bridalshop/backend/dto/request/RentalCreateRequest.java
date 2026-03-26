package edu.bridalshop.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RentalCreateRequest(

        @NotBlank(message = "Dress public ID is required")
        String dressPublicId,

        @NotBlank(message = "Customer public ID is required")
        String customerPublicId,

        @NotBlank(message = "NIC number is required")
        @Size(max = 20, message = "NIC number must be at most 20 characters")
        String nicNumber
) {}