package com.blanchebridal.backend.rental.service;

import com.blanchebridal.backend.rental.dto.req.CreateRentalRequest;
import com.blanchebridal.backend.rental.dto.req.RentalBookingRequest;
import com.blanchebridal.backend.rental.dto.req.UpdateBalanceRequest;
import com.blanchebridal.backend.rental.dto.res.RentalResponse;
import com.blanchebridal.backend.rental.entity.RentalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RentalService {

    RentalResponse createRental(CreateRentalRequest req);

    RentalResponse bookRental(RentalBookingRequest req, UUID callerId);

    Page<RentalResponse> getAllRentals(RentalStatus status, Pageable pageable);

    List<RentalResponse> getMyRentals(UUID userId);

    RentalResponse getRentalById(UUID id, UUID requestingUserId, String role);

    RentalResponse markReturned(UUID id, LocalDate returnDate);

    RentalResponse updateBalance(UUID id, UpdateBalanceRequest req);

    RentalResponse cancelRental(UUID id, UUID userId, String role);

    void markOverdueRentals();

    void markActiveRentals();

    // New: cancels PENDING_PAYMENT rentals (and their synthetic order) once
    // 48h have passed since the requested pickup date with no cash paid.
    void expireStaleBookings();
}