package com.blanchebridal.backend.inquiry.dto.res;

import com.blanchebridal.backend.inquiry.entity.InquiryStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class InquiryResponse {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String subject;
    private String message;
    private String imageUrl;
    private InquiryStatus status;
    private LocalDateTime createdAt;
}