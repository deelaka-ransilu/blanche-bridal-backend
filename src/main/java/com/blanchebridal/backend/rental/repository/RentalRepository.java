package com.blanchebridal.backend.rental.repository;

import com.blanchebridal.backend.rental.entity.Rental;
import com.blanchebridal.backend.rental.entity.RentalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RentalRepository extends JpaRepository<Rental, UUID> {

    List<Rental> findByUser_Id(UUID userId);

    Page<Rental> findByStatus(RentalStatus status, Pageable pageable);

    // Used by overdue detection: ACTIVE rentals whose end date has passed
    List<Rental> findByStatusAndRentalEndBefore(RentalStatus status, LocalDate date);

    // Used to block booking a product that is currently out on rental
    boolean existsByProduct_IdAndStatusIn(UUID productId, List<RentalStatus> statuses);
}