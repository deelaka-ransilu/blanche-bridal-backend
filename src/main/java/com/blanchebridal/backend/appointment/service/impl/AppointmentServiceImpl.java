package com.blanchebridal.backend.appointment.service.impl;

import com.blanchebridal.backend.appointment.dto.req.CreateAppointmentRequest;
import com.blanchebridal.backend.appointment.dto.req.RescheduleAppointmentRequest;
import com.blanchebridal.backend.appointment.dto.res.AppointmentResponse;
import com.blanchebridal.backend.appointment.dto.res.CustomDesignRequestResponse;
import com.blanchebridal.backend.appointment.dto.res.CustomOrderSummaryResponse;
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
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.repository.ProductionStageRecordRepository;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    private final ProductionStageRecordRepository productionStageRecordRepository;
    private final com.blanchebridal.backend.payment.repository.PaymentRepository paymentRepository;

    private static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    private static final String ROLE_CUSTOMER_ALT = "CUSTOMER";

    // Boutique operates on Sri Lanka time (UTC+5:30, no DST) regardless of
    // where the application server is deployed -- all "is this in the
    // past" checks for appointments are evaluated in this zone.
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final long MIN_LEAD_MINUTES = 60;

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

        // Reject bookings in the past or with insufficient lead time,
        // evaluated in the boutique's local timezone (Asia/Colombo)
        // regardless of the server's deployment timezone. This is the
        // authoritative check -- the frontend only mirrors it for UX,
        // since a direct API call could otherwise bypass it entirely.
        ZonedDateTime nowColombo = ZonedDateTime.now(COLOMBO);
        ZonedDateTime requestedDateTime = req.getAppointmentDate()
                .atTime(LocalTime.parse(req.getTimeSlot()))
                .atZone(COLOMBO);

        if (requestedDateTime.isBefore(nowColombo.plusMinutes(MIN_LEAD_MINUTES))) {
            throw new IllegalStateException(
                    "Cannot book a slot in the past or with less than "
                            + MIN_LEAD_MINUTES + " minutes' notice");
        }

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
            if (req.getOccasionDate().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException(
                        "Occasion date cannot be in the past");
            }
            if (!req.getOccasionDate().isAfter(req.getAppointmentDate())) {
                throw new IllegalArgumentException(
                        "Occasion date must be after the consultation date");
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
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse confirmAppointment(UUID id) {
        Appointment appointment = findById(id);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        Appointment saved = appointmentRepository.save(appointment);

        // Fire-and-forget — calendar sync no longer blocks this request.
        googleCalendarService.createEventAsync(saved.getId());

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
        Appointment saved = appointmentRepository.save(appointment);

        try {
            User customer = saved.getUser();
            if (customer != null) {
                emailService.sendAppointmentCancelledEmail(
                        customer.getEmail(),
                        customer.getFirstName() + " " + customer.getLastName(),
                        saved.getAppointmentDate(),
                        saved.getTimeSlot(),
                        saved.getType().name()
                );
            }
        } catch (Exception e) {
            log.warn("[Appointment] Failed to send cancellation email for {}: {}",
                    saved.getId(), e.getMessage());
        }

        return toResponse(saved);
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

    @Override
    @Transactional(readOnly = true)
    public CustomDesignRequestResponse getCustomDesignRequestById(
            UUID customDesignRequestId, UUID requestingUserId, String role) {

        CustomDesignRequest cdr = customDesignRequestRepository.findById(customDesignRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Custom design request not found: " + customDesignRequestId));

        Appointment appointment = cdr.getAppointment();

        // Reuses the same ownership check as getAppointmentById — a customer
        // may only view their own custom design request; admin/employee see
        // any of them.
        validateCustomerAccess(appointment, requestingUserId, role);

        return toCustomDesignRequestResponse(cdr, appointment);
    }

    @Override
    public List<CustomOrderSummaryResponse> getAllCustomOrders() {
        return customDesignRequestRepository.findByFirstPaymentOrderIsNotNullOrderByCreatedAtDesc()
                .stream()
                .map(cdr -> {
                    Order firstOrder = cdr.getFirstPaymentOrder();

                    String stage = productionStageRecordRepository.findByOrderId(firstOrder.getId())
                            .map(r -> r.getCurrentStage().name())
                            .orElse(null);

                    String customerName = null;
                    String customerEmail = null;
                    User user = cdr.getAppointment() != null ? cdr.getAppointment().getUser() : null;
                    if (user != null) {
                        customerName = user.getFirstName() + " " + user.getLastName();
                        customerEmail = user.getEmail();
                    }

                    String paymentStatus = paymentRepository.findByOrder_Id(firstOrder.getId())
                            .map(p -> p.getStatus().name())
                            .orElse("PENDING");

                    return CustomOrderSummaryResponse.builder()
                            .id(cdr.getId())
                            .customerName(customerName)
                            .customerEmail(customerEmail)
                            .occasionDate(cdr.getOccasionDate())
                            .firstPaymentOrderId(firstOrder.getId())
                            .secondPaymentOrderId(cdr.getSecondPaymentOrder() != null
                                    ? cdr.getSecondPaymentOrder().getId() : null)
                            .firstPaymentStatus(paymentStatus)
                            .currentProductionStage(stage)
                            .createdAt(cdr.getCreatedAt())
                            .build();
                })
                .toList();
    }
// 3. New private mapper — place it near toResponse():

    private CustomDesignRequestResponse toCustomDesignRequestResponse(
            CustomDesignRequest cdr, Appointment appointment) {

        String customerName = null;
        String customerEmail = null;
        UUID userId = null;
        if (appointment.getUser() != null) {
            userId = appointment.getUser().getId();
            customerName = appointment.getUser().getFirstName()
                    + " " + appointment.getUser().getLastName();
            customerEmail = appointment.getUser().getEmail();
        }

        return CustomDesignRequestResponse.builder()
                .id(cdr.getId())
                .appointmentId(appointment.getId())
                .userId(userId)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .appointmentDate(appointment.getAppointmentDate())
                .timeSlot(appointment.getTimeSlot())
                .appointmentStatus(appointment.getStatus())
                .appointmentNotes(appointment.getNotes())
                .occasionType(cdr.getOccasionType())
                .occasionDate(cdr.getOccasionDate())
                .stylePreferences(cdr.getStylePreferences())
                .referenceImages(readImagesFromJson(cdr.getReferenceImages()))
                .firstPaymentOrderId(cdr.getFirstPaymentOrder() != null
                        ? cdr.getFirstPaymentOrder().getId() : null)
                .secondPaymentOrderId(cdr.getSecondPaymentOrder() != null
                        ? cdr.getSecondPaymentOrder().getId() : null)
                .createdAt(cdr.getCreatedAt())
                .build();
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