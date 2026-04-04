package com.blanchebridal.backend.user.dto.res;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MeasurementsResponse(
        UUID id,
        String publicId,
        UUID customerId,
        BigDecimal heightWithShoes,
        BigDecimal hollowToHem,
        BigDecimal fullBust,
        BigDecimal underBust,
        BigDecimal naturalWaist,
        BigDecimal fullHip,
        BigDecimal shoulderWidth,
        BigDecimal torsoLength,
        BigDecimal thighCircumference,
        BigDecimal waistToKnee,
        BigDecimal waistToFloor,
        BigDecimal armhole,
        BigDecimal bicepCircumference,
        BigDecimal elbowCircumference,
        BigDecimal wristCircumference,
        BigDecimal sleeveLength,
        BigDecimal upperBust,
        BigDecimal bustApexDistance,
        BigDecimal shoulderToBustPoint,
        BigDecimal neckCircumference,
        BigDecimal trainLength,
        String notes,
        LocalDateTime measuredAt
) {}