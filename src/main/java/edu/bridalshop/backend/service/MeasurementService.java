package edu.bridalshop.backend.service;

import edu.bridalshop.backend.dto.request.MeasurementRequest;
import edu.bridalshop.backend.dto.response.MeasurementResponse;
import edu.bridalshop.backend.entity.CustomerMeasurement;
import edu.bridalshop.backend.entity.User;
import edu.bridalshop.backend.exception.ResourceNotFoundException;
import edu.bridalshop.backend.repository.CustomerMeasurementRepository;
import edu.bridalshop.backend.repository.UserRepository;
import edu.bridalshop.backend.util.PayloadSanitizer;
import edu.bridalshop.backend.util.PublicIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeasurementService {

    private final CustomerMeasurementRepository measurementRepository;
    private final UserRepository                userRepository;
    private final PublicIdGenerator             publicIdGenerator;
    private final PayloadSanitizer              sanitizer;

    // =========================================================================
    // LIST — ADMIN and EMPLOYEE see all, CUSTOMER sees only their own
    // =========================================================================

    @Transactional(readOnly = true)
    public List<MeasurementResponse> getAll(String customerPublicId, String requesterEmail) {
        User requester = resolveUser(requesterEmail);
        verifyReadAccess(customerPublicId, requester);
        return measurementRepository
                .findAllByCustomer_PublicIdOrderByMeasuredAtDesc(customerPublicId)
                .stream()
                .map(MeasurementResponse::from)
                .toList();
    }

    // =========================================================================
    // GET SINGLE — ADMIN and EMPLOYEE see any, CUSTOMER sees only their own
    // =========================================================================

    @Transactional(readOnly = true)
    public MeasurementResponse getOne(String customerPublicId,
                                      String measurementPublicId,
                                      String requesterEmail) {
        User requester = resolveUser(requesterEmail);
        verifyReadAccess(customerPublicId, requester);

        // Scoped lookup: ensures the measurement belongs to this customer
        CustomerMeasurement measurement = measurementRepository
                .findByPublicIdAndCustomer_PublicId(measurementPublicId, customerPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Measurement not found: " + measurementPublicId));

        return MeasurementResponse.from(measurement);
    }

    // =========================================================================
    // CREATE — ADMIN only
    // =========================================================================

    @Transactional
    public MeasurementResponse create(String customerPublicId,
                                      MeasurementRequest request,
                                      String adminEmail) {
        User admin    = resolveUser(adminEmail);
        User customer = findCustomerOrThrow(customerPublicId);

        CustomerMeasurement measurement = CustomerMeasurement.builder()
                .publicId(publicIdGenerator.forMeasurement())
                .customer(customer)
                .recordedBy(admin)
                .notes(request.notes() != null
                        ? sanitizer.sanitizeText(request.notes()) : null)
                .measuredAt(request.measuredAt())
                // All 21 measurement fields
                .heightWithShoes(request.heightWithShoes())
                .hollowToHem(request.hollowToHem())
                .fullBust(request.fullBust())
                .underBust(request.underBust())
                .naturalWaist(request.naturalWaist())
                .fullHip(request.fullHip())
                .shoulderWidth(request.shoulderWidth())
                .torsoLength(request.torsoLength())
                .thighCircumference(request.thighCircumference())
                .waistToKnee(request.waistToKnee())
                .waistToFloor(request.waistToFloor())
                .armhole(request.armhole())
                .bicepCircumference(request.bicepCircumference())
                .elbowCircumference(request.elbowCircumference())
                .wristCircumference(request.wristCircumference())
                .sleeveLength(request.sleeveLength())
                .upperBust(request.upperBust())
                .bustApexDistance(request.bustApexDistance())
                .shoulderToBustPoint(request.shoulderToBustPoint())
                .neckCircumference(request.neckCircumference())
                .trainLength(request.trainLength())
                .build();

        CustomerMeasurement saved = measurementRepository.save(measurement);
        log.info("Measurement created: {} for customer: {} by admin: {}",
                saved.getPublicId(), customerPublicId, admin.getPublicId());
        return MeasurementResponse.from(saved);
    }

    // =========================================================================
    // UPDATE — ADMIN only
    // =========================================================================

    @Transactional
    public MeasurementResponse update(String customerPublicId,
                                      String measurementPublicId,
                                      MeasurementRequest request,
                                      String adminEmail) {
        User admin = resolveUser(adminEmail);

        // Scoped lookup: ensures the measurement belongs to this customer
        CustomerMeasurement measurement = measurementRepository
                .findByPublicIdAndCustomer_PublicId(measurementPublicId, customerPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Measurement not found: " + measurementPublicId));

        measurement.setNotes(request.notes() != null
                ? sanitizer.sanitizeText(request.notes()) : null);
        measurement.setMeasuredAt(request.measuredAt());
        measurement.setRecordedBy(admin);

        // Update all 21 measurement fields
        measurement.setHeightWithShoes(request.heightWithShoes());
        measurement.setHollowToHem(request.hollowToHem());
        measurement.setFullBust(request.fullBust());
        measurement.setUnderBust(request.underBust());
        measurement.setNaturalWaist(request.naturalWaist());
        measurement.setFullHip(request.fullHip());
        measurement.setShoulderWidth(request.shoulderWidth());
        measurement.setTorsoLength(request.torsoLength());
        measurement.setThighCircumference(request.thighCircumference());
        measurement.setWaistToKnee(request.waistToKnee());
        measurement.setWaistToFloor(request.waistToFloor());
        measurement.setArmhole(request.armhole());
        measurement.setBicepCircumference(request.bicepCircumference());
        measurement.setElbowCircumference(request.elbowCircumference());
        measurement.setWristCircumference(request.wristCircumference());
        measurement.setSleeveLength(request.sleeveLength());
        measurement.setUpperBust(request.upperBust());
        measurement.setBustApexDistance(request.bustApexDistance());
        measurement.setShoulderToBustPoint(request.shoulderToBustPoint());
        measurement.setNeckCircumference(request.neckCircumference());
        measurement.setTrainLength(request.trainLength());

        CustomerMeasurement saved = measurementRepository.save(measurement);
        log.info("Measurement updated: {} by admin: {}", saved.getPublicId(), admin.getPublicId());
        return MeasurementResponse.from(saved);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resolves a User entity from the email stored in the JWT principal (getUsername()).
     * Spring Security stores the email as the username in CustomUserDetailsService.
     */
    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + email));
    }

    /**
     * Access rules:
     * - ADMIN and EMPLOYEE can view any customer's measurements
     * - CUSTOMER can only view their own measurements
     */
    private void verifyReadAccess(String customerPublicId, User requester) {
        String role = requester.getRole().name();
        if ("CUSTOMER".equals(role) && !requester.getPublicId().equals(customerPublicId)) {
            throw new AccessDeniedException(
                    "Customers can only view their own measurements");
        }
    }

    private User findCustomerOrThrow(String customerPublicId) {
        User customer = userRepository.findByPublicId(customerPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found: " + customerPublicId));

        if (!"CUSTOMER".equals(customer.getRole().name())) {
            throw new IllegalArgumentException(
                    "User " + customerPublicId + " is not a CUSTOMER");
        }

        return customer;
    }
}