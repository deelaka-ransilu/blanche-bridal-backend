package com.blanchebridal.backend.rental.repository;

import com.blanchebridal.backend.rental.entity.Rental;
import com.blanchebridal.backend.rental.entity.RentalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentalRepository extends JpaRepository<Rental, UUID> {

    List<Rental> findByUser_Id(UUID userId);

    Page<Rental> findByStatus(RentalStatus status, Pageable pageable);

    List<Rental> findByStatusAndRentalEndBefore(RentalStatus status, LocalDate date);

    boolean existsByProduct_IdAndStatusIn(UUID productId, List<RentalStatus> statuses);

    // First (fitting) payment order lookup — used by PaymentServiceImpl to
    // flip PENDING_PAYMENT -> BOOKED once the 50% fitting payment completes.
    Optional<Rental> findByOrder_Id(UUID orderId);

    // Second (handover) payment order lookup — used by PaymentServiceImpl to
    // confirm handover (remaining 50% + security deposit) and set the rental
    // ACTIVE.
    Optional<Rental> findByHandoverOrder_Id(UUID orderId);

    List<Rental> findByStatusAndRentalStartLessThanEqual(RentalStatus status, LocalDate date);
}