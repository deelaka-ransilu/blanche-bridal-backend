package com.blanchebridal.backend.product.dto.req;

import jakarta.validation.constraints.NotBlank;

public record ProductImageInput(
        @NotBlank String url,
        @NotBlank String publicId
) {}