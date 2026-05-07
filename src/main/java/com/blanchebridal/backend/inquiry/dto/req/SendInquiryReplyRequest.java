package com.blanchebridal.backend.inquiry.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendInquiryReplyRequest {
    @NotBlank
    private String message;
}