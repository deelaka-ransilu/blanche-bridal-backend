package com.blanchebridal.backend.payment.repository;

import com.blanchebridal.backend.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrder_Id(UUID orderId);
    Optional<Payment> findByPayhereOrderId(String payhereOrderId);
}