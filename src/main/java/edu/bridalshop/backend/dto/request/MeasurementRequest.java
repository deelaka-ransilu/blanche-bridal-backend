package edu.bridalshop.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MeasurementRequest(

        @NotNull(message = "measuredAt is required")
        LocalDateTime measuredAt,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes,

        // All measurement fields are optional — not every field is taken each session
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal heightWithShoes,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal hollowToHem,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal fullBust,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal underBust,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal naturalWaist,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal fullHip,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal shoulderWidth,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal torsoLength,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal thighCircumference,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal waistToKnee,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal waistToFloor,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal armhole,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal bicepCircumference,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal elbowCircumference,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal wristCircumference,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal sleeveLength,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal upperBust,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal bustApexDistance,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal shoulderToBustPoint,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal neckCircumference,
        @DecimalMin(value = "0.0", message = "Must be positive") BigDecimal trainLength
) {}