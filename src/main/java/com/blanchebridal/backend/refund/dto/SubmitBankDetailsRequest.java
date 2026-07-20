package com.blanchebridal.backend.refund.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitBankDetailsRequest {
    @NotBlank
    private String accountHolderName;
    @NotBlank
    private String accountNumber;
    @NotBlank
    private String bankName;
    private String branch; // optional
}