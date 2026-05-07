package com.blanchebridal.backend.user.service.impl;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.user.dto.req.CreateUserRequest;
import com.blanchebridal.backend.user.dto.req.CreateWalkInCustomerRequest;
import com.blanchebridal.backend.user.dto.req.MeasurementsRequest;
import com.blanchebridal.backend.user.dto.req.UpdateCustomerProfileRequest;
import com.blanchebridal.backend.user.dto.res.CustomerDetailResponse;
import com.blanchebridal.backend.user.dto.res.MeasurementsResponse;
import com.blanchebridal.backend.user.dto.res.UserResponse;
import com.blanchebridal.backend.user.entity.CustomerMeasurement;
import com.blanchebridal.backend.user.entity.CustomerProfile;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.repository.CustomerMeasurementRepository;
import com.blanchebridal.backend.user.repository.CustomerProfileRepository;
import com.blanchebridal.backend.user.repository.UserRepository;
import com.blanchebridal.backend.user.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final CustomerMeasurementRepository measurementRepository;
    private final CustomerProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Sequence helper for public_id ──────────────────────────────────────
    // In production this would use the DB sequence; here we derive from count.
    private String generatePublicId() {
        long count = measurementRepository.count() + 1;
        return String.format("MEAS-%05d", count);
    }

    // ── Employees ──────────────────────────────────────────────────────────

    @Override
    public List<UserResponse> listAdmins() {
        return userRepository.findByRole(UserRole.ADMIN)
                .stream().map(this::toUserResponse).toList();
    }

    @Override
    @Transactional
    public UserResponse createAdmin(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(UserRole.ADMIN)
                .isActive(true)
                .build();
        return toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse deactivateAdmin(UUID adminId) {
        User user = findUserByIdAndRole(adminId, UserRole.ADMIN);
        user.setIsActive(false);
        return toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse activateAdmin(UUID adminId) {
        User user = findUserByIdAndRole(adminId, UserRole.ADMIN);
        user.setIsActive(true);
        return toUserResponse(userRepository.save(user));
    }

    @Override
    public List<UserResponse> listEmployees() {
        return userRepository.findByRole(UserRole.EMPLOYEE)
                .stream().map(this::toUserResponse).toList();
    }

    @Override
    @Transactional
    public UserResponse createEmployee(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(UserRole.EMPLOYEE)
                .isActive(true)
                .build();
        return toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse activateEmployee(UUID employeeId) {
        User user = findUserByIdAndRole(employeeId, UserRole.EMPLOYEE);
        user.setIsActive(true);
        return toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse deactivateEmployee(UUID employeeId) {
        User user = findUserByIdAndRole(employeeId, UserRole.EMPLOYEE);
        user.setIsActive(false);
        return toUserResponse(userRepository.save(user));
    }

    // ── Customers — basic ──────────────────────────────────────────────────

    @Override
    public List<UserResponse> listCustomers() {
        return userRepository.findByRole(UserRole.CUSTOMER)
                .stream().map(this::toUserResponse).toList();
    }

    @Override
    public UserResponse getCustomer(UUID customerId) {
        return toUserResponse(findUserByIdAndRole(customerId, UserRole.CUSTOMER));
    }

    @Override
    @Transactional
    public UserResponse activateCustomer(UUID customerId) {
        User user = findUserByIdAndRole(customerId, UserRole.CUSTOMER);
        user.setIsActive(true);
        return toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse deactivateCustomer(UUID customerId) {
        User user = findUserByIdAndRole(customerId, UserRole.CUSTOMER);
        user.setIsActive(false);
        return toUserResponse(userRepository.save(user));
    }

    // ── Walk-in customer creation ──────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse createWalkInCustomer(CreateWalkInCustomerRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ConflictException("A customer with this email already exists");
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(null) // walk-in: no password yet
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();
        User saved = userRepository.save(user);
        // create an empty profile row immediately
        CustomerProfile profile = CustomerProfile.builder()
                .customer(saved)
                .adminNotes(null)
                .designImageUrls(new ArrayList<>())
                .build();
        profileRepository.save(profile);
        return toUserResponse(saved);
    }

    // ── Customer detail ────────────────────────────────────────────────────

    @Override
    public CustomerDetailResponse getCustomerDetail(UUID customerId) {
        User customer = findUserByIdAndRole(customerId, UserRole.CUSTOMER);
        CustomerProfile profile = profileRepository.findByCustomerId(customerId)
                .orElseGet(() -> CustomerProfile.builder()
                        .customer(customer)
                        .adminNotes(null)
                        .designImageUrls(new ArrayList<>())
                        .build());
        List<MeasurementsResponse> measurements = measurementRepository
                .findAllByCustomerIdOrderByMeasuredAtDesc(customerId)
                .stream().map(this::toMeasurementsResponse).toList();
        return toCustomerDetail(customer, profile, measurements);
    }

    // ── Customer profile update ────────────────────────────────────────────

    @Override
    @Transactional
    public CustomerDetailResponse updateCustomerProfile(UUID customerId, UpdateCustomerProfileRequest request) {
        User customer = findUserByIdAndRole(customerId, UserRole.CUSTOMER);
        CustomerProfile profile = profileRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    CustomerProfile p = CustomerProfile.builder()
                            .customer(customer)
                            .designImageUrls(new ArrayList<>())
                            .build();
                    return profileRepository.save(p);
                });
        if (request.adminNotes() != null) {
            profile.setAdminNotes(request.adminNotes());
        }
        if (request.designImageUrls() != null) {
            profile.setDesignImageUrls(request.designImageUrls());
        }
        profileRepository.save(profile);
        List<MeasurementsResponse> measurements = measurementRepository
                .findAllByCustomerIdOrderByMeasuredAtDesc(customerId)
                .stream().map(this::toMeasurementsResponse).toList();
        return toCustomerDetail(customer, profile, measurements);
    }

    // ── Measurements ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public MeasurementsResponse addMeasurement(UUID customerId, MeasurementsRequest req, UUID recordedByAdminId) {
        User customer = findUserByIdAndRole(customerId, UserRole.CUSTOMER);
        User admin = userRepository.findById(recordedByAdminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        CustomerMeasurement m = CustomerMeasurement.builder()
                .publicId(generatePublicId())
                .customer(customer)
                .recordedBy(admin)
                .notes(req.notes())
                .measuredAt(LocalDateTime.now())
                .heightWithShoes(req.heightWithShoes())
                .hollowToHem(req.hollowToHem())
                .fullBust(req.fullBust())
                .underBust(req.underBust())
                .naturalWaist(req.naturalWaist())
                .fullHip(req.fullHip())
                .shoulderWidth(req.shoulderWidth())
                .torsoLength(req.torsoLength())
                .thighCircumference(req.thighCircumference())
                .waistToKnee(req.waistToKnee())
                .waistToFloor(req.waistToFloor())
                .armhole(req.armhole())
                .bicepCircumference(req.bicepCircumference())
                .elbowCircumference(req.elbowCircumference())
                .wristCircumference(req.wristCircumference())
                .sleeveLength(req.sleeveLength())
                .upperBust(req.upperBust())
                .bustApexDistance(req.bustApexDistance())
                .shoulderToBustPoint(req.shoulderToBustPoint())
                .neckCircumference(req.neckCircumference())
                .trainLength(req.trainLength())
                .build();
        return toMeasurementsResponse(measurementRepository.save(m));
    }

    @Override
    @Transactional
    public MeasurementsResponse updateMeasurement(UUID customerId, UUID measurementId, MeasurementsRequest req) {
        CustomerMeasurement m = measurementRepository.findById(measurementId)
                .orElseThrow(() -> new ResourceNotFoundException("Measurement not found"));
        if (!m.getCustomer().getId().equals(customerId)) {
            throw new ResourceNotFoundException("Measurement not found for this customer");
        }
        m.setNotes(req.notes());
        m.setHeightWithShoes(req.heightWithShoes());
        m.setHollowToHem(req.hollowToHem());
        m.setFullBust(req.fullBust());
        m.setUnderBust(req.underBust());
        m.setNaturalWaist(req.naturalWaist());
        m.setFullHip(req.fullHip());
        m.setShoulderWidth(req.shoulderWidth());
        m.setTorsoLength(req.torsoLength());
        m.setThighCircumference(req.thighCircumference());
        m.setWaistToKnee(req.waistToKnee());
        m.setWaistToFloor(req.waistToFloor());
        m.setArmhole(req.armhole());
        m.setBicepCircumference(req.bicepCircumference());
        m.setElbowCircumference(req.elbowCircumference());
        m.setWristCircumference(req.wristCircumference());
        m.setSleeveLength(req.sleeveLength());
        m.setUpperBust(req.upperBust());
        m.setBustApexDistance(req.bustApexDistance());
        m.setShoulderToBustPoint(req.shoulderToBustPoint());
        m.setNeckCircumference(req.neckCircumference());
        m.setTrainLength(req.trainLength());
        return toMeasurementsResponse(measurementRepository.save(m));
    }

    @Override
    public List<MeasurementsResponse> listMeasurements(UUID customerId) {
        findUserByIdAndRole(customerId, UserRole.CUSTOMER); // verify exists
        return measurementRepository
                .findAllByCustomerIdOrderByMeasuredAtDesc(customerId)
                .stream().map(this::toMeasurementsResponse).toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private User findUserByIdAndRole(UUID id, UserRole role) {
        return userRepository.findById(id)
                .filter(user -> user.getRole() == role)
                .orElseThrow(() -> new ResourceNotFoundException(
                        role.name().charAt(0) + role.name().substring(1).toLowerCase() + " not found"));
    }

    private UserResponse toUserResponse(User u) {
        return new UserResponse(
                u.getId(), u.getEmail(), u.getRole().name(),
                u.getFirstName(), u.getLastName(), u.getPhone(),
                u.getAddress(),    // ← add this line
                u.getIsActive(), u.getCreatedAt()
        );
    }

    private MeasurementsResponse toMeasurementsResponse(CustomerMeasurement m) {
        return new MeasurementsResponse(
                m.getId(), m.getPublicId(), m.getCustomer().getId(),
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

    private CustomerDetailResponse toCustomerDetail(User u, CustomerProfile p, List<MeasurementsResponse> ms) {
        return new CustomerDetailResponse(
                u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getPhone(), u.getIsActive(), u.getCreatedAt(),
                p.getAdminNotes(),
                p.getDesignImageUrls() != null ? p.getDesignImageUrls() : new ArrayList<>(),
                ms
        );
    }
}