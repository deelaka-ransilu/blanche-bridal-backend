package com.blanchebridal.backend.order.dto.req;

import jakarta.validation.constraints.NotBlank;

public record RejectQuoteRequest(
        @NotBlank String reason
) {
}