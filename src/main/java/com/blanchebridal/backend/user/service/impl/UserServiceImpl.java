package com.blanchebridal.backend.user.service.impl;

import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.user.entity.CustomerMeasurement;
import com.blanchebridal.backend.user.repository.CustomerMeasurementRepository;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.repository.UserRepository;
import com.blanchebridal.backend.user.dto.req.MeasurementsRequest;
import com.blanchebridal.backend.user.dto.req.UpdateProfileRequest;
import com.blanchebridal.backend.user.dto.res.MeasurementsResponse;
import com.blanchebridal.backend.user.dto.res.UserResponse;
import com.blanchebridal.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CustomerMeasurementRepository measurementRepository;

    // ── Profile ───────────────────────────────────────────────────────────────

    @Override
    public UserResponse getProfile(UUID userId) {
        return toUserResponse(findUserById(userId));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUserById(userId);
        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName()  != null) user.setLastName(request.lastName());
        if (request.phone()     != null) user.setPhone(request.phone());
        if (request.address()   != null) user.setAddress(request.address());
        return toUserResponse(userRepository.save(user));
    }

    // ── Measurements ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MeasurementsResponse saveMeasurements(UUID customerId, UUID recordedById,
                                                 MeasurementsRequest request) {
        User customer   = findUserById(customerId);
        User recordedBy = findUserById(recordedById);

        String publicId = generatePublicId(customerId);

        CustomerMeasurement m = CustomerMeasurement.builder()
                .publicId(publicId)
                .customer(customer)
                .recordedBy(recordedBy)
                .measuredAt(LocalDateTime.now())
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
                .notes(request.notes())
                .build();

        return toMeasurementsResponse(measurementRepository.save(m));
    }

    @Override
    public List<MeasurementsResponse> getMeasurements(UUID customerId) {
        findUserById(customerId);
        return measurementRepository
                .findByCustomer_IdOrderByMeasuredAtDesc(customerId)
                .stream()
                .map(this::toMeasurementsResponse)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private String generatePublicId(UUID customerId) {
        long count = measurementRepository.countByCustomer_Id(customerId);
        return String.format("MEAS-%04d", count + 1);
    }

    private UserResponse toUserResponse(User u) {
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getRole().name(),
                u.getFirstName(),
                u.getLastName(),
                u.getPhone(),
                u.getAddress(),   // ← this was missing
                u.getIsActive(),
                u.getCreatedAt()
        );
    }

    private MeasurementsResponse toMeasurementsResponse(CustomerMeasurement m) {
        return new MeasurementsResponse(
                m.getId(),
                m.getPublicId(),
                m.getCustomer().getId(),
                m.getHeightWithShoes(), m.getHollowToHem(),
                m.getFullBust(), m.getUnderBust(), m.getNaturalWaist(), m.getFullHip(),
                m.getShoulderWidth(), m.getTorsoLength(), m.getThighCircumference(),
                m.getWaistToKnee(), m.getWaistToFloor(), m.getArmhole(),
                m.getBicepCircumference(), m.getElbowCircumference(), m.getWristCircumference(),
                m.getSleeveLength(), m.getUpperBust(), m.getBustApexDistance(),
                m.getShoulderToBustPoint(), m.getNeckCircumference(), m.getTrainLength(),
                m.getNotes(), m.getMeasuredAt()
        );
    }
}