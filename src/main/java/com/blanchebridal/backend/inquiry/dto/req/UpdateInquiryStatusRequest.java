package com.blanchebridal.backend.inquiry.dto.req;

import com.blanchebridal.backend.inquiry.entity.InquiryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateInquiryStatusRequest {

    @NotNull(message = "Status is required")
    private InquiryStatus status;
}