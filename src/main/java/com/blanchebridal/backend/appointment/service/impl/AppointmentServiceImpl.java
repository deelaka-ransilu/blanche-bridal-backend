package com.blanchebridal.backend.appointment.service.impl;

import com.blanchebridal.backend.appointment.dto.req.CreateAppointmentRequest;
import com.blanchebridal.backend.appointment.dto.req.RescheduleAppointmentRequest;
import com.blanchebridal.backend.appointment.dto.res.AppointmentResponse;
import com.blanchebridal.backend.appointment.entity.Appointment;
import com.blanchebridal.backend.appointment.entity.AppointmentStatus;
import com.blanchebridal.backend.appointment.entity.AppointmentType;
import com.blanchebridal.backend.appointment.entity.CustomDesignRequest;
import com.blanchebridal.backend.appointment.entity.TimeSlotConfig;
import com.blanchebridal.backend.appointment.repository.AppointmentRepository;
import com.blanchebridal.backend.appointment.repository.CustomDesignRequestRepository;
import com.blanchebridal.backend.appointment.repository.TimeSlotConfigRepository;
import com.blanchebridal.backend.appointment.service.AppointmentService;
import com.blanchebridal.backend.appointment.service.GoogleCalendarService;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CustomDesignRequestRepository customDesignRequestRepository;
    private final TimeSlotConfigRepository timeSlotConfigRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final GoogleCalendarService googleCalendarService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    private static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    private static final String ROLE_CUSTOMER_ALT = "CUSTOMER";

    @Override
    @Transactional(readOnly = true)
    public List<String> getAvailableSlots(LocalDate date) {
        // 1=Mon ... 7=Sun (ISO standard, same as DB)
        int dayOfWeek = date.getDayOfWeek().getValue();

        List<String> configuredSlots = timeSlotConfigRepository
                .findByDayOfWeekAndIsActiveTrue(dayOfWeek)
                .stream()
                .map(TimeSlotConfig::getSlotTime)
                .toList();

        // All noncancelled bookings on this date consume a slot
        Set<String> bookedSlots = appointmentRepository
                .findByAppointmentDateAndStatusNot(date, AppointmentStatus.CANCELLED)
                .stream()
                .map(Appointment::getTimeSlot)
                .collect(Collectors.toSet());

        return configuredSlots.stream()
                .filter(slot -> !bookedSlots.contains(slot))
                .sorted()
                .toList();
    }

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(CreateAppointmentRequest req, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Race-condition guard — check slot is still free
        boolean slotTaken = appointmentRepository
                .existsByAppointmentDateAndTimeSlotAndStatusNot(
                        req.getAppointmentDate(), req.getTimeSlot(), AppointmentStatus.CANCELLED);
        if (slotTaken) {
            throw new IllegalStateException(
                    "Time slot " + req.getTimeSlot() + " on " + req.getAppointmentDate()
                            + " is no longer available");
        }

        Product product = null;
        if (req.getProductId() != null) {
            product = productRepository.findById(req.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        }

        // Custom-design-only fields are required at the service layer, not
        // via @NotNull on the DTO, because they're meaningless for the
        // other three appointment types — see CreateAppointmentRequest.
        if (req.getType() == AppointmentType.CUSTOM_CONSULTATION) {
            if (req.getOccasionType() == null) {
                throw new IllegalArgumentException(
                        "Occasion type is required for a custom design consultation");
            }
            if (req.getOccasionDate() == null) {
                throw new IllegalArgumentException(
                        "Occasion date is required for a custom design consultation");
            }
        }

        Appointment appointment = Appointment.builder()
                .user(user)
                .product(product)
                .appointmentDate(req.getAppointmentDate())
                .timeSlot(req.getTimeSlot())
                .type(req.getType())
                .notes(req.getNotes())
                .build();

        Appointment saved = appointmentRepository.save(appointment);

        if (req.getType() == AppointmentType.CUSTOM_CONSULTATION) {
            CustomDesignRequest customDesignRequest = CustomDesignRequest.builder()
                    .appointment(saved)
                    .occasionType(req.getOccasionType())
                    .occasionDate(req.getOccasionDate())
                    .stylePreferences(req.getStylePreferences())
                    .referenceImages(writeImagesAsJson(req.getReferenceImages()))
                    .build();
            customDesignRequestRepository.save(customDesignRequest);
        }

        try {
            emailService.sendAppointmentBookingReceivedEmail(
                    user.getEmail(),
                    user.getFirstName() + " " + user.getLastName(),
                    saved.getId(),
                    saved.getAppointmentDate(),
                    saved.getTimeSlot(),
                    saved.getType().name(),
                    saved.getProduct() != null ? saved.getProduct().getName() : null
            );
        } catch (Exception e) {
            log.warn("[Appointment] Failed to send booking received email for {}: {}",
                    saved.getId(), e.getMessage());
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse confirmAppointment(UUID id) {
        Appointment appointment = findById(id);
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        String eventId = googleCalendarService.createEvent(appointment);
        appointment.setGoogleEventId(eventId);

        Appointment saved = appointmentRepository.save(appointment);

        try {
            User customer = saved.getUser();
            if (customer != null) {
                emailService.sendAppointmentConfirmationEmail(
                        customer.getEmail(),
                        customer.getFirstName() + " " + customer.getLastName(),
                        saved.getAppointmentDate(),
                        saved.getTimeSlot(),
                        saved.getType().name(),
                        saved.getProduct() != null ? saved.getProduct().getName() : null
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send appointment confirmation email for {}: {}",
                    saved.getId(), e.getMessage());
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse cancelAppointment(UUID id, UUID requestingUserId, String role) {
        Appointment appointment = findById(id);
        validateCustomerAccess(appointment, requestingUserId, role);

        boolean isCustomer = role != null &&
                (role.equals(ROLE_CUSTOMER) || role.equals(ROLE_CUSTOMER_ALT));

        if (isCustomer && (appointment.getUser() == null ||
                !appointment.getUser().getId().equals(requestingUserId))) {
            throw new UnauthorizedException("Access denied to this appointment");
        }

        if (appointment.getGoogleEventId() != null) {
            googleCalendarService.deleteEvent(appointment.getGoogleEventId());
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    @Transactional
    public AppointmentResponse rescheduleAppointment(
            UUID id, RescheduleAppointmentRequest req,
            UUID requestingUserId, String role) {

        Appointment appointment = findById(id);
        validateCustomerAccess(appointment, requestingUserId, role);

        // Check new slot is free
        boolean slotTaken = appointmentRepository
                .existsByAppointmentDateAndTimeSlotAndStatusNot(
                        req.getAppointmentDate(), req.getTimeSlot(), AppointmentStatus.CANCELLED);
        if (slotTaken) {
            throw new IllegalStateException(
                    "Time slot " + req.getTimeSlot() + " on " + req.getAppointmentDate()
                            + " is no longer available");
        }

        appointment.setAppointmentDate(req.getAppointmentDate());
        appointment.setTimeSlot(req.getTimeSlot());

        // A customer-initiated reschedule invalidates the prior confirmation --
        // the new time hasn't been reviewed by the boutique yet, so it drops
        // back to PENDING pending admin re-confirmation. Admin/employee-initiated
        // reschedules are already authoritative and keep their current status.
        boolean isCustomer = role != null &&
                (role.equals(ROLE_CUSTOMER) || role.equals(ROLE_CUSTOMER_ALT));
        if (isCustomer && appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            appointment.setStatus(AppointmentStatus.PENDING);
        }

        if (appointment.getGoogleEventId() != null) {
            googleCalendarService.updateEvent(appointment.getGoogleEventId(), appointment);
        }

        Appointment saved = appointmentRepository.save(appointment);

        try {
            User customer = saved.getUser();
            if (customer != null) {
                emailService.sendAppointmentRescheduledEmail(
                        customer.getEmail(),
                        customer.getFirstName() + " " + customer.getLastName(),
                        saved.getAppointmentDate(),
                        saved.getTimeSlot(),
                        saved.getType().name()
                );
            }
        } catch (Exception e) {
            log.warn("[Appointment] Failed to send reschedule email for {}: {}",
                    saved.getId(), e.getMessage());
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse completeAppointment(UUID id) {
        Appointment appointment = findById(id);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAllAppointments(
            AppointmentStatus status, Pageable pageable) {
        Page<Appointment> page = status != null
                ? appointmentRepository.findByStatus(status, pageable)
                : appointmentRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getMyAppointments(UUID userId, Pageable pageable) {
        return appointmentRepository.findByUser_Id(userId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(
            UUID id, UUID requestingUserId, String role) {
        Appointment appointment = findById(id);
        validateCustomerAccess(appointment, requestingUserId, role);

        return toResponse(appointment);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private Appointment findById(UUID id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found: " + id));
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        String customerName = null;
        String customerEmail = null;
        if (appointment.getUser() != null) {
            customerName = appointment.getUser().getFirstName()
                    + " " + appointment.getUser().getLastName();
            customerEmail = appointment.getUser().getEmail();
        }

        AppointmentResponse.AppointmentResponseBuilder builder = AppointmentResponse.builder()
                .id(appointment.getId())
                .userId(appointment.getUser() != null ? appointment.getUser().getId() : null)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .productId(appointment.getProduct() != null
                        ? appointment.getProduct().getId() : null)
                .productName(appointment.getProduct() != null
                        ? appointment.getProduct().getName() : null)
                .appointmentDate(appointment.getAppointmentDate())
                .timeSlot(appointment.getTimeSlot())
                .type(appointment.getType())
                .status(appointment.getStatus())
                .googleEventId(appointment.getGoogleEventId())
                .notes(appointment.getNotes())
                .createdAt(appointment.getCreatedAt());

        // Only queried for CUSTOM_CONSULTATION appointments — avoids an
        // extra lookup per row for the other three (majority) types when
        // this is called from a paginated list (getAllAppointments /
        // getMyAppointments). Still one extra query per consultation row
        // (N+1 if a page is all consultations) — acceptable at current
        // scale, revisit with a join/fetch if consultation volume grows.
        if (appointment.getType() == AppointmentType.CUSTOM_CONSULTATION) {
            customDesignRequestRepository.findByAppointment_Id(appointment.getId())
                    .ifPresent(cdr -> builder
                            .occasionType(cdr.getOccasionType())
                            .occasionDate(cdr.getOccasionDate())
                            .stylePreferences(cdr.getStylePreferences())
                            .referenceImages(readImagesFromJson(cdr.getReferenceImages())));
        }

        return builder.build();
    }

    private void validateCustomerAccess(
            Appointment appointment, UUID requestingUserId, String role) {

        boolean isCustomer = role != null &&
                (role.equals(ROLE_CUSTOMER) || role.equals(ROLE_CUSTOMER_ALT));

        if (isCustomer && (appointment.getUser() == null ||
                !appointment.getUser().getId().equals(requestingUserId))) {
            throw new UnauthorizedException("Access denied to this appointment");
        }
    }

    // Same JSON-array-in-TEXT-column convention as Product.sizes — see
    // CustomDesignRequest.referenceImages.

    private String writeImagesAsJson(List<String> images) {
        if (images == null || images.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            log.warn("[Appointment] Failed to serialize reference images: {}", e.getMessage());
            return null;
        }
    }

    private List<String> readImagesFromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("[Appointment] Failed to parse reference images: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}