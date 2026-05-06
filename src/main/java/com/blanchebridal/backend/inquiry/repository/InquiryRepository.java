package com.blanchebridal.backend.inquiry.repository;

import com.blanchebridal.backend.inquiry.entity.Inquiry;
import com.blanchebridal.backend.inquiry.entity.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {
    Page<Inquiry> findByStatus(InquiryStatus status, Pageable pageable);
    Page<Inquiry> findByEmail(String email, Pageable pageable);
}