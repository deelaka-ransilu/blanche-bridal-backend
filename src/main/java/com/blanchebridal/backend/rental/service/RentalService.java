package com.blanchebridal.backend.rental.service;

import com.blanchebridal.backend.order.dto.res.OrderResponse;
import com.blanchebridal.backend.rental.dto.req.CreateRentalBookingRequest;
import com.blanchebridal.backend.rental.dto.req.CreateRentalRequest;
import com.blanchebridal.backend.rental.dto.req.HandoverRequest;
import com.blanchebridal.backend.rental.dto.req.MarkReturnedRequest;
import com.blanchebridal.backend.rental.dto.req.RentalBookingRequest;
import com.blanchebridal.backend.rental.dto.req.UpdateBalanceRequest;
import com.blanchebridal.backend.rental.dto.res.RentableProductResponse;
import com.blanchebridal.backend.rental.dto.res.RentalResponse;
import com.blanchebridal.backend.rental.entity.RentalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface RentalService {

    RentalResponse createRental(CreateRentalRequest req);

    RentalResponse bookRental(RentalBookingRequest req, UUID callerId);

    Page<RentalResponse> getAllRentals(RentalStatus status, Pageable pageable);

    List<RentalResponse> getMyRentals(UUID userId);

    RentalResponse getRentalById(UUID id, UUID requestingUserId, String role);

    // Replaces the old direct markReturned(id, date) — now takes the full
    // request so damage/damageCost flow through to the refund calculation.
    RentalResponse markReturned(UUID id, MarkReturnedRequest req);

    RentalResponse updateBalance(UUID id, UpdateBalanceRequest req);

    RentalResponse cancelRental(UUID id, UUID userId, String role);

    // ADMIN/EMPLOYEE — confirms handover at pickup: creates the second
    // synthetic order (remaining 50% rental fee + security deposit).
    RentalResponse confirmHandover(UUID id, HandoverRequest req, UUID callerId, String role);

    void markOverdueRentals();

    void markActiveRentals();

    void expireStaleBookings();

    List<RentableProductResponse> getRentableProducts();

    OrderResponse createRentalBooking(CreateRentalBookingRequest req, UUID callerId, String role);
}