package com.blanchebridal.backend.refund.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BankDetailsResponse {
    private String accountHolderName;
    private String accountNumber;
    private String bankName;
    private String branch;
    private LocalDateTime submittedAt;
}