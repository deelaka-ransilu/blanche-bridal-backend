package com.blanchebridal.backend.inquiry.controller;

import com.blanchebridal.backend.inquiry.dto.req.CreateInquiryRequest;
import com.blanchebridal.backend.inquiry.dto.req.UpdateInquiryStatusRequest;
import com.blanchebridal.backend.inquiry.dto.res.InquiryResponse;
import com.blanchebridal.backend.inquiry.entity.InquiryStatus;
import com.blanchebridal.backend.inquiry.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    // PUBLIC — guests can submit without a token
    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody CreateInquiryRequest req) {
        log.info("[Inquiry] Submission from {} <{}>", req.getName(), req.getEmail());
        InquiryResponse response = inquiryService.submitInquiry(req);
        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }

    // ADMIN + EMPLOYEE
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','EMPLOYEE')")
    public ResponseEntity<?> getAll(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<InquiryResponse> result = inquiryService.getAllInquiries(
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.getContent(),
                "pagination", Map.of(
                        "page", result.getNumber(),
                        "size", result.getSize(),
                        "total", result.getTotalElements(),
                        "totalPages", result.getTotalPages())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','EMPLOYEE')")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", inquiryService.getInquiryById(id)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','EMPLOYEE')")
    public ResponseEntity<?> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInquiryStatusRequest req) {

        log.info("[Inquiry] Status update request — inquiry: {}, new status: {}",
                id, req.getStatus());
        return ResponseEntity.ok(Map.of("success", true,
                "data", inquiryService.updateStatus(id, req.getStatus())));
    }
}