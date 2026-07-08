package com.blanchebridal.backend.order.repository;

import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByUser_Id(UUID userId, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime cutoff);

    // Added for Financial Reporting (Step 9c, FR-FR-01/02/04) -- reports need
    // full lists (not Page<>) since they're aggregated in-memory by month,
    // not paginated for display.
    List<Order> findByStatusAndCreatedAtBetween(OrderStatus status, LocalDateTime from, LocalDateTime to);
    List<Order> findByDiscountTypeIsNotNullAndCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}