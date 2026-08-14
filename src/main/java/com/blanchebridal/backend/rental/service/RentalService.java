package com.blanchebridal.backend.rental.service;

import com.blanchebridal.backend.order.dto.res.OrderResponse;
import com.blanchebridal.backend.rental.dto.req.*;
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

    RentalResponse markReturned(UUID id, MarkReturnedRequest req);

    RentalResponse cancelRental(UUID id, UUID userId, String role);

    // ADMIN — ADVANCE only: confirms handover at pickup, creates the second
    // synthetic order (remaining 50% of dressValue). SAME_DAY rentals never
    // call this — they go PENDING_PAYMENT -> ACTIVE directly on their single
    // payment confirming.
    RentalResponse confirmHandover(UUID id, HandoverRequest req, UUID callerId, String role);

    void markOverdueRentals();

    void markActiveRentals();

    void expireStaleBookings();

    List<RentableProductResponse> getRentableProducts();

    OrderResponse createRentalBooking(CreateRentalBookingRequest req, UUID callerId, String role);

    RentalResponse updateNotes(UUID id, UpdateRentalNotesRequest req);
}