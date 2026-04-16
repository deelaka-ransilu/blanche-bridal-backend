package com.blanchebridal.backend.payment.dto.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentStatusResponse {
    private String status;
}