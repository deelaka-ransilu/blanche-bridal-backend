package com.blanchebridal.backend.user.service.impl;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.repository.UserRepository;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.dto.req.CreateUserRequest;
import com.blanchebridal.backend.user.dto.res.UserResponse;
import com.blanchebridal.backend.user.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;   // ← NEW

    // ── Superadmin manages admins ─────────────────────────────────────────────

    @Override
    public List<UserResponse> listAdmins() {
        return userRepository.findByRole(UserRole.ADMIN)
                .stream().map(this::toUserResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponse createAdmin(CreateUserRequest request) {
        UserResponse response = createUser(request, UserRole.ADMIN);

        // Send welcome email with credentials — fire and forget, never block account creation
        try {
            emailService.sendAdminWelcomeEmail(
                    request.email(),
                    request.firstName(),
                    request.lastName(),
                    request.password()   // plain password — before it's hashed
            );
        } catch (Exception e) {
            log.warn("Admin created but welcome email failed for {}: {}", request.email(), e.getMessage());
        }

        return response;
    }

    @Override
    @Transactional
    public UserResponse deactivateAdmin(UUID adminId) {
        return deactivateUser(adminId, UserRole.ADMIN);
    }

    @Override
    @Transactional
    public UserResponse activateAdmin(UUID adminId) {
        return activateUser(adminId, UserRole.ADMIN);
    }

    // ── Admin manages employees ───────────────────────────────────────────────

    @Override
    public List<UserResponse> listEmployees() {
        return userRepository.findByRole(UserRole.EMPLOYEE)
                .stream().map(this::toUserResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponse createEmployee(CreateUserRequest request) {
        UserResponse response = createUser(request, UserRole.EMPLOYEE);

        try {
            emailService.sendAdminWelcomeEmail(
                    request.email(),
                    request.firstName(),
                    request.lastName(),
                    request.password()
            );
        } catch (Exception e) {
            log.warn("Employee created but welcome email failed for {}: {}",
                    request.email(), e.getMessage());
        }

        return response;
    }

    @Override
    @Transactional
    public UserResponse deactivateEmployee(UUID employeeId) {
        return deactivateUser(employeeId, UserRole.EMPLOYEE);
    }

    @Override
    @Transactional
    public UserResponse activateEmployee(UUID employeeId) {
        return activateUser(employeeId, UserRole.EMPLOYEE);
    }

    // ── Admin views customers ─────────────────────────────────────────────────

    @Override
    public List<UserResponse> listCustomers() {
        return userRepository.findByRole(UserRole.CUSTOMER)
                .stream().map(this::toUserResponse).collect(Collectors.toList());
    }

    @Override
    public UserResponse getCustomer(UUID customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found: " + customerId));
        return toUserResponse(customer);
    }

    @Override
    @Transactional
    public UserResponse activateCustomer(UUID customerId) {
        return activateUser(customerId, UserRole.CUSTOMER);
    }

    @Override
    @Transactional
    public UserResponse deactivateCustomer(UUID customerId) {
        return deactivateUser(customerId, UserRole.CUSTOMER);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private UserResponse createUser(CreateUserRequest request, UserRole role) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(role)
                .isActive(true)
                .build();
        return toUserResponse(userRepository.save(user));
    }

    private UserResponse deactivateUser(UUID userId, UserRole expectedRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + userId));
        if (user.getRole() != expectedRole) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        user.setIsActive(false);
        return toUserResponse(userRepository.save(user));
    }

    private UserResponse activateUser(UUID userId, UserRole expectedRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + userId));
        if (user.getRole() != expectedRole) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        user.setIsActive(true);
        return toUserResponse(userRepository.save(user));
    }

    private UserResponse toUserResponse(User u) {
        return new UserResponse(
                u.getId(), u.getEmail(), u.getRole().name(),
                u.getFirstName(), u.getLastName(), u.getPhone(),
                u.getIsActive(), u.getCreatedAt()
        );
    }
}