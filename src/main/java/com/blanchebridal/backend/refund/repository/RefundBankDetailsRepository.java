package com.blanchebridal.backend.refund.repository;

import com.blanchebridal.backend.refund.entity.RefundBankDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefundBankDetailsRepository extends JpaRepository<RefundBankDetails, UUID> {
    Optional<RefundBankDetails> findByOrder_Id(UUID orderId);
    boolean existsByOrder_Id(UUID orderId);
}