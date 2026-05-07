package com.blanchebridal.backend.inquiry.service.impl;

import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.inquiry.dto.req.CreateInquiryRequest;
import com.blanchebridal.backend.inquiry.dto.res.InquiryResponse;
import com.blanchebridal.backend.inquiry.entity.Inquiry;
import com.blanchebridal.backend.inquiry.entity.InquiryStatus;
import com.blanchebridal.backend.inquiry.repository.InquiryRepository;
import com.blanchebridal.backend.inquiry.service.InquiryService;
import com.blanchebridal.backend.shared.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryServiceImpl implements InquiryService {

    private final InquiryRepository inquiryRepository;
    private final EmailService emailService;

    @Override
    public InquiryResponse submitInquiry(CreateInquiryRequest req) {
        Inquiry inquiry = Inquiry.builder()
                .name(req.getName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .subject(req.getSubject())
                .message(req.getMessage())
                .imageUrl(req.getImageUrl())
                .status(InquiryStatus.OPEN)
                .build();

        Inquiry saved = inquiryRepository.save(inquiry);
        log.info("[Inquiry] New inquiry {} submitted by {} <{}>",
                saved.getId(), saved.getName(), saved.getEmail());
        return toResponse(saved);
    }

    @Override
    public Page<InquiryResponse> getAllInquiries(InquiryStatus status, Pageable pageable) {
        if (status != null) {
            return inquiryRepository.findByStatus(status, pageable).map(this::toResponse);
        }
        return inquiryRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public InquiryResponse getInquiryById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public InquiryResponse updateStatus(UUID id, InquiryStatus newStatus) {
        Inquiry inquiry = findOrThrow(id);
        inquiry.setStatus(newStatus);
        Inquiry saved = inquiryRepository.save(inquiry);
        log.info("[Inquiry] Status updated → {} for inquiry {}", newStatus, id);
        return toResponse(saved);
    }

    @Override
    public Page<InquiryResponse> getInquiriesByEmail(String email, Pageable pageable) {
        return inquiryRepository.findByEmail(email, pageable).map(this::toResponse);
    }

    @Override
    public void replyToInquiry(UUID id, String replyMessage) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found"));

        emailService.sendInquiryReplyEmail(
                inquiry.getEmail(),
                inquiry.getName(),
                inquiry.getMessage(),
                replyMessage
        );

        // Auto-mark as IN_PROGRESS if still OPEN
        if (inquiry.getStatus() == InquiryStatus.OPEN) {
            inquiry.setStatus(InquiryStatus.IN_PROGRESS);
            inquiryRepository.save(inquiry);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Inquiry findOrThrow(UUID id) {
        return inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found: " + id));
    }

    private InquiryResponse toResponse(Inquiry i) {
        return InquiryResponse.builder()
                .id(i.getId())
                .name(i.getName())
                .email(i.getEmail())
                .phone(i.getPhone())
                .subject(i.getSubject())
                .message(i.getMessage())
                .imageUrl(i.getImageUrl())
                .status(i.getStatus())
                .createdAt(i.getCreatedAt())
                .build();
    }
}