package com.blanchebridal.backend.rental.dto.res;

import com.blanchebridal.backend.rental.entity.RentalStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RentalResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String productImage;
    private UUID userId;
    private String customerName;
    private String customerEmail;

    private UUID orderId;          // fitting (first) payment order
    private UUID handoverOrderId;  // handover (second) payment order

    private LocalDate rentalStart;
    private LocalDate rentalEnd;
    private LocalDate returnDate;
    private RentalStatus status;

    private BigDecimal rentalFee;
    private BigDecimal securityDepositAmount;
    private BigDecimal securityDepositRefundedAmount;
    private BigDecimal damageCost;
    private BigDecimal lateFeeAmount;
    private BigDecimal amountOwedByCustomer;
    private LocalDateTime handoverConfirmedAt;

    private BigDecimal depositAmount; // legacy field, kept for old rows
    private BigDecimal balanceDue;
    private String notes;
    private LocalDateTime createdAt;

    // Fitting appointment fields — renamed from appointmentDate/TimeSlot/Id
    // for clarity now that this is unambiguously the fitting, not a pickup.
    private LocalDate fittingDate;
    private String fittingTimeSlot;
    private UUID fittingAppointmentId;
}