package com.blanchebridal.backend.refund.dto;

import lombok.Data;

@Data
public class RefundRequest {
    private String reason;
    private String proofImageUrl; // required — validated in service, not here
}