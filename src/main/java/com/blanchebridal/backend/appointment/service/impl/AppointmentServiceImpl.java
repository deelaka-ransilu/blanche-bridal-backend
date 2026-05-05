package com.blanchebridal.backend.appointment.service.impl;

import com.blanchebridal.backend.appointment.dto.req.CreateAppointmentRequest;
import com.blanchebridal.backend.appointment.dto.req.RescheduleAppointmentRequest;
import com.blanchebridal.backend.appointment.dto.res.AppointmentResponse;
import com.blanchebridal.backend.appointment.entity.Appointment;
import com.blanchebridal.backend.appointment.entity.AppointmentStatus;
import com.blanchebridal.backend.appointment.entity.TimeSlotConfig;
import com.blanchebridal.backend.appointment.repository.AppointmentRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final TimeSlotConfigRepository timeSlotConfigRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final GoogleCalendarService googleCalendarService;
    private final EmailService emailService;

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

        // All non-cancelled bookings on this date consume a slot
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

        Appointment appointment = Appointment.builder()
                .user(user)
                .product(product)
                .appointmentDate(req.getAppointmentDate())
                .timeSlot(req.getTimeSlot())
                .type(req.getType())
                .notes(req.getNotes())
                .build();

        Appointment saved = appointmentRepository.save(appointment);

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
                        saved.getId(),                    // ← ADD THIS LINE
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

        boolean isCustomer = role != null &&
                (role.equals("ROLE_CUSTOMER") || role.equals("CUSTOMER"));

        if (isCustomer) {
            if (appointment.getUser() == null ||
                    !appointment.getUser().getId().equals(requestingUserId)) {
                throw new UnauthorizedException("Access denied to this appointment");
            }
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

        boolean isCustomer = role != null &&
                (role.equals("ROLE_CUSTOMER") || role.equals("CUSTOMER"));

        if (isCustomer) {
            if (appointment.getUser() == null ||
                    !appointment.getUser().getId().equals(requestingUserId)) {
                throw new UnauthorizedException("Access denied to this appointment");
            }
        }

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

        if (appointment.getGoogleEventId() != null) {
            googleCalendarService.updateEvent(appointment.getGoogleEventId(), appointment);
        }

        return toResponse(appointmentRepository.save(appointment));
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

        boolean isCustomer = role != null &&
                (role.equals("ROLE_CUSTOMER") || role.equals("CUSTOMER"));

        if (isCustomer) {
            if (appointment.getUser() == null ||
                    !appointment.getUser().getId().equals(requestingUserId)) {
                throw new UnauthorizedException("Access denied to this appointment");
            }
        }

        return toResponse(appointment);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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

        return AppointmentResponse.builder()
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
                .createdAt(appointment.getCreatedAt())
                .build();
    }
}