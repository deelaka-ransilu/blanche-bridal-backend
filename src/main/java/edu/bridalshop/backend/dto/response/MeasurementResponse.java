package edu.bridalshop.backend.dto.response;

import edu.bridalshop.backend.entity.CustomerMeasurement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MeasurementResponse(
        String publicId,
        String customerPublicId,
        String customerName,
        String recordedByPublicId,
        String recordedByName,
        String notes,
        LocalDateTime measuredAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,

        // All 21 measurement fields
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
        BigDecimal trainLength
) {
    public static MeasurementResponse from(CustomerMeasurement m) {
        return new MeasurementResponse(
                m.getPublicId(),
                m.getCustomer().getPublicId(),
                m.getCustomer().getFullName(),
                m.getRecordedBy().getPublicId(),
                m.getRecordedBy().getFullName(),
                m.getNotes(),
                m.getMeasuredAt(),
                m.getCreatedAt(),
                m.getUpdatedAt(),
                m.getHeightWithShoes(),
                m.getHollowToHem(),
                m.getFullBust(),
                m.getUnderBust(),
                m.getNaturalWaist(),
                m.getFullHip(),
                m.getShoulderWidth(),
                m.getTorsoLength(),
                m.getThighCircumference(),
                m.getWaistToKnee(),
                m.getWaistToFloor(),
                m.getArmhole(),
                m.getBicepCircumference(),
                m.getElbowCircumference(),
                m.getWristCircumference(),
                m.getSleeveLength(),
                m.getUpperBust(),
                m.getBustApexDistance(),
                m.getShoulderToBustPoint(),
                m.getNeckCircumference(),
                m.getTrainLength()
        );
    }
}