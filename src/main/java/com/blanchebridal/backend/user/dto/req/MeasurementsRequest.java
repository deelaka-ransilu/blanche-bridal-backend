package com.blanchebridal.backend.user.dto.req;

import java.math.BigDecimal;

public record MeasurementsRequest(
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
        String notes
) {}