package com.blanchebridal.backend.rental.dto.res;

import com.blanchebridal.backend.payment.entity.PaymentMethod;
import com.blanchebridal.backend.rental.entity.RentalBookingPath;
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

    private UUID orderId;          // ADVANCE: first (50%) payment. SAME_DAY: the only payment.
    private UUID handoverOrderId;  // ADVANCE only — second (remaining 50%) payment.

    private PaymentMethod paymentMethod;          // first/booking order's payment method
    private PaymentMethod handoverPaymentMethod;   // handover order's payment method (ADVANCE only)
    private RentalBookingPath bookingPath;

    private LocalDate rentalStart;
    private LocalDate rentalEnd;
    private LocalDate returnDate;
    private RentalStatus status;

    private BigDecimal dressValue;
    private BigDecimal rentalFee;
    private BigDecimal damageCost;
    private BigDecimal lateFeeAmount;
    private BigDecimal refundAmount;
    private BigDecimal amountOwedByCustomer;
    private LocalDateTime handoverConfirmedAt;

    private String notes;
    private LocalDateTime createdAt;

    // Fitting fields — ADVANCE only, null for SAME_DAY (no fitting visit).
    private LocalDate fittingDate;
    private String fittingTimeSlot;
    private UUID fittingAppointmentId;
}