package com.blanchebridal.backend.refund.repository;

import com.blanchebridal.backend.refund.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    boolean existsByOrder_Id(UUID orderId);

    Optional<Refund> findByOrder_Id(UUID orderId);

    // Added for Financial Reporting (Step 9c, FR-FR-03/04).
    List<Refund> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}