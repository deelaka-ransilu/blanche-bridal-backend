package com.blanchebridal.backend.rental.dto.res;

import lombok.Builder;
import java.time.LocalDate;

@Builder
public record BlockedDateRangeResponse(LocalDate start, LocalDate end) {}