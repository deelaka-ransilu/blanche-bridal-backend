package com.blanchebridal.backend.payment.repository;

import com.blanchebridal.backend.payment.entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Optional<Receipt> findByOrder_Id(UUID orderId);
    List<Receipt> findByOrder_User_Id(UUID userId);
    Page<Receipt> findAll(Pageable pageable);
    @Query("SELECT r.receiptNumber FROM Receipt r ORDER BY r.issuedAt DESC LIMIT 1")
    Optional<String> findLatestReceiptNumber();
}