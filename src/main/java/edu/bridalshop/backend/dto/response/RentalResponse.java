package edu.bridalshop.backend.dto.response;

import edu.bridalshop.backend.entity.Rental;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RentalResponse(
        String publicId,
        String dressPublicId,
        String dressName,
        String customerPublicId,
        String customerName,
        String createdByPublicId,
        String createdByName,
        String nicNumber,

        // Price snapshots
        BigDecimal rentalPricePerDay,
        Integer rentalPeriodDays,
        BigDecimal depositAmount,
        BigDecimal totalRentalFee,
        BigDecimal totalPaidUpfront,

        // Lifecycle
        LocalDateTime handedOverAt,
        LocalDate dueDate,
        LocalDateTime returnedAt,

        // Return financials
        Integer daysLate,
        BigDecimal lateFine,
        BigDecimal totalDamageCost,
        BigDecimal totalDeductions,
        BigDecimal depositRefunded,
        BigDecimal outstandingBalance,

        // Status
        String status,
        String returnNotes,

        List<DamageItemResponse> damageItems,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RentalResponse from(Rental r) {
        return new RentalResponse(
                r.getPublicId(),
                r.getDress().getPublicId(),
                r.getDress().getName(),
                r.getCustomer().getPublicId(),
                r.getCustomer().getFullName(),
                r.getCreatedBy().getPublicId(),
                r.getCreatedBy().getFullName(),
                r.getNicNumber(),
                r.getRentalPricePerDay(),
                r.getRentalPeriodDays(),
                r.getDepositAmount(),
                r.getTotalRentalFee(),
                r.getTotalPaidUpfront(),
                r.getHandedOverAt(),
                r.getDueDate(),
                r.getReturnedAt(),
                r.getDaysLate(),
                r.getLateFine(),
                r.getTotalDamageCost(),
                r.getTotalDeductions(),
                r.getDepositRefunded(),
                r.getOutstandingBalance(),
                r.getStatus(),
                r.getReturnNotes(),
                r.getDamageItems().stream()
                        .map(DamageItemResponse::from)
                        .toList(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}