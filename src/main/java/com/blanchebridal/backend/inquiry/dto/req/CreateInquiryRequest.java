package com.blanchebridal.backend.inquiry.dto.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateInquiryRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    private String email;

    private String phone;
    private String subject;

    @NotBlank(message = "Message is required")
    private String message;

    private String imageUrl;
}