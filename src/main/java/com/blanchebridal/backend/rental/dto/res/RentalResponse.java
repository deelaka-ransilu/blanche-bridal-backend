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
    private UUID orderId;
    private LocalDate rentalStart;
    private LocalDate rentalEnd;
    private LocalDate returnDate;
    private RentalStatus status;
    private BigDecimal depositAmount;
    private BigDecimal balanceDue;
    private String notes;
    private LocalDateTime createdAt;
}