package com.blanchebridal.backend.inquiry.service;

import com.blanchebridal.backend.inquiry.dto.req.CreateInquiryRequest;
import com.blanchebridal.backend.inquiry.dto.res.InquiryResponse;
import com.blanchebridal.backend.inquiry.entity.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InquiryService {
    InquiryResponse submitInquiry(CreateInquiryRequest req);
    Page<InquiryResponse> getAllInquiries(InquiryStatus status, Pageable pageable);
    InquiryResponse getInquiryById(UUID id);
    InquiryResponse updateStatus(UUID id, InquiryStatus newStatus);
    Page<InquiryResponse> getInquiriesByEmail(String email, Pageable pageable);
}