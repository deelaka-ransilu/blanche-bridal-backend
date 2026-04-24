package com.blanchebridal.backend.appointment;

import com.blanchebridal.backend.appointment.dto.req.CreateAppointmentRequest;
import com.blanchebridal.backend.appointment.dto.req.RescheduleAppointmentRequest;
import com.blanchebridal.backend.appointment.dto.res.AppointmentResponse;
import com.blanchebridal.backend.appointment.entity.Appointment;
import com.blanchebridal.backend.appointment.entity.AppointmentStatus;
import com.blanchebridal.backend.appointment.entity.AppointmentType;
import com.blanchebridal.backend.appointment.entity.TimeSlotConfig;
import com.blanchebridal.backend.appointment.repository.AppointmentRepository;
import com.blanchebridal.backend.appointment.repository.TimeSlotConfigRepository;
import com.blanchebridal.backend.appointment.service.GoogleCalendarService;
import com.blanchebridal.backend.appointment.service.impl.AppointmentServiceImpl;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentServiceImpl Tests")
class AppointmentServiceImplTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private TimeSlotConfigRepository timeSlotConfigRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private GoogleCalendarService googleCalendarService;
    @Mock private EmailService emailService;

    @InjectMocks
    private AppointmentServiceImpl appointmentService;

    private User customer;
    private Product product;
    private Appointment appointment;
    private UUID customerId;
    private UUID productId;
    private UUID appointmentId;
    private LocalDate futureDate;

    @BeforeEach
    void setUp() {
        customerId     = UUID.randomUUID();
        productId      = UUID.randomUUID();
        appointmentId  = UUID.randomUUID();
        futureDate     = LocalDate.now().plusDays(3);

        customer = User.builder()
                .id(customerId)
                .email("customer@example.com")
                .firstName("Amaya")
                .lastName("Silva")
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();

        product = Product.builder()
                .id(productId)
                .name("Ivory Lace Gown")
                .images(new ArrayList<>())
                .build();

        appointment = Appointment.builder()
                .id(appointmentId)
                .user(customer)
                .product(product)
                .appointmentDate(futureDate)
                .timeSlot("10:00")
                .type(AppointmentType.FITTING)
                .status(AppointmentStatus.PENDING)
                .build();
    }

    // ── getAvailableSlots ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getAvailableSlots — returns configured slots minus booked slots")
    void getAvailableSlots_returnsAvailableOnly() {
        TimeSlotConfig slot1 = new TimeSlotConfig();
        slot1.setSlotTime("10:00");
        TimeSlotConfig slot2 = new TimeSlotConfig();
        slot2.setSlotTime("11:00");
        TimeSlotConfig slot3 = new TimeSlotConfig();
        slot3.setSlotTime("14:00");

        // 10:00 already booked
        Appointment booked = Appointment.builder()
                .timeSlot("10:00")
                .status(AppointmentStatus.CONFIRMED)
                .build();

        when(timeSlotConfigRepository.findByDayOfWeekAndIsActiveTrue(anyInt()))
                .thenReturn(List.of(slot1, slot2, slot3));
        when(appointmentRepository.findByAppointmentDateAndStatusNot(
                eq(futureDate), eq(AppointmentStatus.CANCELLED)))
                .thenReturn(List.of(booked));

        List<String> slots = appointmentService.getAvailableSlots(futureDate);

        assertThat(slots).containsExactly("11:00", "14:00");
        assertThat(slots).doesNotContain("10:00");
    }

    @Test
    @DisplayName("getAvailableSlots — returns empty list for Sunday (no configured slots)")
    void getAvailableSlots_sunday_returnsEmpty() {
        when(timeSlotConfigRepository.findByDayOfWeekAndIsActiveTrue(7))
                .thenReturn(List.of());

        LocalDate sunday = LocalDate.now().with(java.time.DayOfWeek.SUNDAY).plusWeeks(1);
        List<String> slots = appointmentService.getAvailableSlots(sunday);

        assertThat(slots).isEmpty();
    }

    @Test
    @DisplayName("getAvailableSlots — cancelled appointments free up their slot")
    void getAvailableSlots_cancelledAppointment_slotIsAvailable() {
        TimeSlotConfig slot = new TimeSlotConfig();
        slot.setSlotTime("10:00");

        when(timeSlotConfigRepository.findByDayOfWeekAndIsActiveTrue(anyInt()))
                .thenReturn(List.of(slot));
        when(appointmentRepository.findByAppointmentDateAndStatusNot(
                eq(futureDate), eq(AppointmentStatus.CANCELLED)))
                .thenReturn(List.of()); // no non-cancelled bookings

        List<String> slots = appointmentService.getAvailableSlots(futureDate);

        assertThat(slots).contains("10:00");
    }

    // ── bookAppointment ───────────────────────────────────────────────────────

    @Test
    @DisplayName("bookAppointment — creates appointment with PENDING status")
    void bookAppointment_validRequest_createsPending() {
        CreateAppointmentRequest req = new CreateAppointmentRequest();
        req.setAppointmentDate(futureDate);
        req.setTimeSlot("10:00");
        req.setType(AppointmentType.FITTING);

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(appointmentRepository.existsByAppointmentDateAndTimeSlotAndStatusNot(
                eq(futureDate), eq("10:00"), eq(AppointmentStatus.CANCELLED)))
                .thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(appointment);

        AppointmentResponse response = appointmentService.bookAppointment(req, customerId);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(response.getTimeSlot()).isEqualTo("10:00");
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    @DisplayName("bookAppointment — throws IllegalStateException when slot already taken")
    void bookAppointment_slotTaken_throwsException() {
        CreateAppointmentRequest req = new CreateAppointmentRequest();
        req.setAppointmentDate(futureDate);
        req.setTimeSlot("10:00");
        req.setType(AppointmentType.FITTING);

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(appointmentRepository.existsByAppointmentDateAndTimeSlotAndStatusNot(
                eq(futureDate), eq("10:00"), eq(AppointmentStatus.CANCELLED)))
                .thenReturn(true);

        assertThatThrownBy(() -> appointmentService.bookAppointment(req, customerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer available");

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("bookAppointment — throws ResourceNotFoundException when user not found")
    void bookAppointment_userNotFound_throwsException() {
        CreateAppointmentRequest req = new CreateAppointmentRequest();
        req.setAppointmentDate(futureDate);
        req.setTimeSlot("10:00");
        req.setType(AppointmentType.FITTING);

        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.bookAppointment(req, customerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("bookAppointment — links product when productId is provided")
    void bookAppointment_withProduct_linksProduct() {
        CreateAppointmentRequest req = new CreateAppointmentRequest();
        req.setAppointmentDate(futureDate);
        req.setTimeSlot("10:00");
        req.setType(AppointmentType.FITTING);
        req.setProductId(productId);

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(appointmentRepository.existsByAppointmentDateAndTimeSlotAndStatusNot(
                any(), any(), any())).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(appointment);

        AppointmentResponse response = appointmentService.bookAppointment(req, customerId);

        assertThat(response.getProductName()).isEqualTo("Ivory Lace Gown");
    }

    // ── confirmAppointment ────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmAppointment — sets CONFIRMED, creates calendar event, sends email")
    void confirmAppointment_pending_confirmsAndSyncsCalendar() {
        when(appointmentRepository.findById(appointmentId))
                .thenReturn(Optional.of(appointment));
        when(googleCalendarService.createEvent(any(Appointment.class)))
                .thenReturn("google-event-id-123");
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(
                Appointment.builder()
                        .id(appointmentId)
                        .user(customer)
                        .product(product)
                        .appointmentDate(futureDate)
                        .timeSlot("10:00")
                        .type(AppointmentType.FITTING)
                        .status(AppointmentStatus.CONFIRMED)
                        .googleEventId("google-event-id-123")
                        .build());

        AppointmentResponse response = appointmentService.confirmAppointment(appointmentId);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(response.getGoogleEventId()).isEqualTo("google-event-id-123");
        verify(googleCalendarService).createEvent(any(Appointment.class));
        verify(emailService).sendAppointmentConfirmationEmail(
                eq(customer.getEmail()),
                eq("Amaya Silva"),
                eq(futureDate),
                eq("10:00"),
                eq("FITTING"),
                eq("Ivory Lace Gown"));
    }

    @Test
    @DisplayName("confirmAppointment — email failure does not prevent confirmation")
    void confirmAppointment_emailFails_statusStillConfirmed() {
        when(appointmentRepository.findById(appointmentId))
                .thenReturn(Optional.of(appointment));
        when(googleCalendarService.createEvent(any())).thenReturn("event-id");
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(
                Appointment.builder()
                        .id(appointmentId)
                        .user(customer)
                        .product(product)
                        .appointmentDate(futureDate)
                        .timeSlot("10:00")
                        .type(AppointmentType.FITTING)
                        .status(AppointmentStatus.CONFIRMED)
                        .googleEventId("event-id")
                        .build());
        doThrow(new RuntimeException("SMTP error"))
                .when(emailService).sendAppointmentConfirmationEmail(
                        any(), any(), any(), any(), any(), any());

        AppointmentResponse response = appointmentService.confirmAppointment(appointmentId);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    // ── cancelAppointment ─────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelAppointment — admin can cancel any appointment")
    void cancelAppointment_adminRole_cancelsAny() {
        Appointment confirmed = Appointment.builder()
                .id(appointmentId).user(customer).product(product)
                .appointmentDate(futureDate).timeSlot("10:00")
                .type(AppointmentType.FITTING)
                .status(AppointmentStatus.CONFIRMED)
                .googleEventId("event-id-123")
                .build();

        when(appointmentRepository.findById(appointmentId))
                .thenReturn(Optional.of(confirmed));
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(
                Appointment.builder()
                        .id(appointmentId).user(customer)
                        .status(AppointmentStatus.CANCELLED).build());

        AppointmentResponse response = appointmentService.cancelAppointment(
                appointmentId, UUID.randomUUID(), "ROLE_ADMIN");

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(googleCalendarService).deleteEvent("event-id-123");
    }

    @Test
    @DisplayName("cancelAppointment — customer can cancel own appointment")
    void cancelAppointment_customerOwnAppointment_cancels() {
        when(appointmentRepository.findById(appointmentId))
                .thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(
                Appointment.builder()
                        .id(appointmentId).user(customer)
                        .status(AppointmentStatus.CANCELLED).build());

        AppointmentResponse response = appointmentService.cancelAppointment(
                appointmentId, customerId, "ROLE_CUSTOMER");

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelAppointment — customer cannot cancel another customer's appointment")
    void cancelAppointment_customerOtherAppointment_throwsException() {
        when(appointmentRepository.findById(appointmentId))
                .thenReturn(Optional.of(appointment));

        UUID otherId = UUID.randomUUID();

        assertThatThrownBy(() ->
                appointmentService.cancelAppointment(appointmentId, otherId, "ROLE_CUSTOMER"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Access denied");

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelAppointment — deletes calendar event when googleEventId present")
    void cancelAppointment_withGoogleEventId_deletesCalendarEvent() {
        Appointment withEvent = Appointment.builder()
                .id(appointmentId).user(customer).product(product)
                .appointmentDate(futureDate).timeSlot("10:00")
                .type(AppointmentType.FITTING)
                .status(AppointmentStatus.CONFIRMED)
                .googleEventId("event-abc-123")
                .build();

        when(appointmentRepository.findById(appointmentId))
                .thenReturn(Optional.of(withEvent));
        when(appointmentRepository.save(any())).thenReturn(
                Appointment.builder()
                        .id(appointmentId).user(customer)
                        .status(AppointmentStatus.CANCELLED).build());

        appointmentService.cancelAppointment(appointmentId, UUID.randomUUID(), "ROLE_ADMIN");

        verify(googleCalendarService).deleteEvent("event-abc-123");
    }

    @Test
    @DisplayName("cancelAppointment — no calendar call when googleEventId is null")
    void cancelAppointment_noGoogleEventId_noCalendarCall() {
        when(appointmentRepository.findById(appointmentId))
                .thenReturn(Optional.of(appointment)); // no googleEventId
        when(appointmentRepository.save(any())).thenReturn(
                Appointment.builder()
                        .id(appointmentId).user(customer)
                        .status(AppointmentStatus.CANCELLED).build());

        appointmentService.cancelAppointment(appointmentId, customerId, "ROLE_CUSTOMER");

        verifyNoInteractions(googleCalendarService);
    }

    // ── rescheduleAppointment ─────────────────────────────────────────────────

    @Test
    @DisplayName("rescheduleAppointment — updates date and slot when new slot is free")
    void rescheduleAppointment_slotFree_updatesDateAndSlot() {
        LocalDate newDate = futureDate.plusDays(1);
        RescheduleAppointmentRequest req = new RescheduleAppointmentRequest();
        req.setAppointmentDate(newDate);
        req.setTimeSlot("14:00");

        when(appointmentRepository.findById(appointmentId))
                .thenReturn(Optional.of(appointment));
        when(appointmentRepository.existsByAppointmentDateAndTimeSlotAndStatusNot(
                eq(newDate), eq("14:00"), eq(AppointmentStatus.CANCELLED)))
                .thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(
                Appointment.builder()
                        .id(appointmentId).user(customer).product(product)
                        .appointmentDate(newDate).timeSlot("14:00")
                        .type(AppointmentType.FITTING)
                        .status(AppointmentStatus.PENDING).build());

        AppointmentResponse response = appointmentService.rescheduleAppointment(
                appointmentId, req, customerId, "ROLE_CUSTOMER");

        assertThat(response.getAppointmentDate()).isEqualTo(newDate);
        assertThat(response.getTimeSlot()).isEqualTo("14:00");
    }

    @Test
    @DisplayName("rescheduleAppointment — throws IllegalStateException when new slot is taken")
    void rescheduleAppointment_slotTaken_throwsException() {
        LocalDate newDate = futureDate.plusDays(1);
        RescheduleAppointmentRequest req = new RescheduleAppointmentRequest();
        req.setAppointmentDate(newDate);
        req.setTimeSlot("14:00");

        when(appointmentRepository.findById(appointmentId))
                .thenReturn(Optional.of(appointment));
        when(appointmentRepository.existsByAppointmentDateAndTimeSlotAndStatusNot(
                eq(newDate), eq("14:00"), eq(AppointmentStatus.CANCELLED)))
                .thenReturn(true);

        assertThatThrownBy(() -> appointmentService.rescheduleAppointment(
                appointmentId, req, customerId, "ROLE_CUSTOMER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer available");

        verify(appointmentRepository, never()).save(any());
    }

    // ── completeAppointment ───────────────────────────────────────────────────

    @Test
    @DisplayName("completeAppointment — sets status to COMPLETED")
    void completeAppointment_confirmed_setsCompleted() {
        Appointment confirmed = Appointment.builder()
                .id(appointmentId).user(customer).product(product)
                .appointmentDate(futureDate).timeSlot("10:00")
                .type(AppointmentType.FITTING)
                .status(AppointmentStatus.CONFIRMED).build();

        when(appointmentRepository.findById(appointmentId))
                .thenReturn(Optional.of(confirmed));
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(
                Appointment.builder()
                        .id(appointmentId).user(customer).product(product)
                        .appointmentDate(futureDate).timeSlot("10:00")
                        .type(AppointmentType.FITTING)
                        .status(AppointmentStatus.COMPLETED).build());

        AppointmentResponse response = appointmentService.completeAppointment(appointmentId);

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    @DisplayName("completeAppointment — throws ResourceNotFoundException when not found")
    void completeAppointment_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(appointmentRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.completeAppointment(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }
}