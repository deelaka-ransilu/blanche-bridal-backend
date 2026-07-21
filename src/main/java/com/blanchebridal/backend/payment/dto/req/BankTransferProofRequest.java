package com.blanchebridal.backend.payment.dto.req;

import jakarta.validation.constraints.NotBlank;

public record BankTransferProofRequest(
        @NotBlank String url,
        String publicId // optional, kept for parity with Cloudinary response shape; not persisted since Payment has no field for it
) {
}