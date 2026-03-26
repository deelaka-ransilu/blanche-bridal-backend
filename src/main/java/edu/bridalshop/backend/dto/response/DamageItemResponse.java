package edu.bridalshop.backend.dto.response;

import edu.bridalshop.backend.entity.RentalDamageItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DamageItemResponse(
        Integer damageId,
        String description,
        BigDecimal estimatedCost,
        LocalDateTime createdAt
) {
    public static DamageItemResponse from(RentalDamageItem item) {
        return new DamageItemResponse(
                item.getDamageId(),
                item.getDescription(),
                item.getEstimatedCost(),
                item.getCreatedAt()
        );
    }
}