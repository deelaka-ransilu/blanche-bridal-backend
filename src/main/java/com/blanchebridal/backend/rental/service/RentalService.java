package com.blanchebridal.backend.rental.service;

import com.blanchebridal.backend.rental.dto.req.CreateRentalRequest;
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

    Page<RentalResponse> getAllRentals(RentalStatus status, Pageable pageable);

    List<RentalResponse> getMyRentals(UUID userId);

    RentalResponse getRentalById(UUID id, UUID requestingUserId, String role);

    RentalResponse markReturned(UUID id, LocalDate returnDate);

    RentalResponse updateBalance(UUID id, UpdateBalanceRequest req);

    void markOverdueRentals();
}