package com.blanchebridal.backend.order.dto.req;

import com.blanchebridal.backend.order.entity.SplitType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateCustomQuoteRequest(
        @NotNull BigDecimal fabricAmount,
        @NotNull BigDecimal laborAmount,
        @NotNull BigDecimal embellishmentAmount,
        @NotNull BigDecimal alterationsAmount,
        @NotNull BigDecimal otherAmount,
        String otherNote,
        @NotNull SplitType splitType
) {
    // otherNote is required only when otherAmount > 0 — enforced in
    // CustomQuoteServiceImpl.createQuote(), not here, since it's a
    // cross-field rule (matches how the codebase already handles similar
    // conditional validation, e.g. ProductServiceImpl's category/price
    // matching, rather than a custom Bean Validation annotation).
}