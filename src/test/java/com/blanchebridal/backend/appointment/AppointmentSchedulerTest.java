package com.blanchebridal.backend.appointment;

import com.blanchebridal.backend.appointment.entity.Appointment;
import com.blanchebridal.backend.appointment.entity.AppointmentStatus;
import com.blanchebridal.backend.appointment.entity.AppointmentType;
import com.blanchebridal.backend.appointment.repository.AppointmentRepository;
import com.blanchebridal.backend.appointment.scheduler.AppointmentScheduler;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentScheduler")
class AppointmentSchedulerTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private EmailService          emailService;

    @InjectMocks
    private AppointmentScheduler appointmentScheduler;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(UUID.randomUUID())
                .email("bride@test.com")
                .firstName("Nadia")
                .lastName("Perera")
                .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Appointment confirmedAppointment(LocalDate date) {
        return Appointment.builder()
                .id(UUID.randomUUID())
                .user(customer)
                .appointmentDate(date)
                .timeSlot("10:00 AM")
                .type(AppointmentType.FITTING)
                .status(AppointmentStatus.CONFIRMED)
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  sendAppointmentReminders
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("sends reminder email for every CONFIRMED appointment scheduled for tomorrow")
    void sendsRemindersForTomorrowsAppointments() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Appointment appt1 = confirmedAppointment(tomorrow);
        Appointment appt2 = confirmedAppointment(tomorrow);

        when(appointmentRepository.findByAppointmentDateAndStatus(
                tomorrow, AppointmentStatus.CONFIRMED))
                .thenReturn(List.of(appt1, appt2));

        appointmentScheduler.sendAppointmentReminders();

        verify(emailService, times(2)).sendAppointmentReminderEmail(
                eq("bride@test.com"),
                eq("Nadia Perera"),
                eq(tomorrow),
                eq("10:00 AM"),
                eq("FITTING")
        );
    }

    @Test
    @DisplayName("does not call emailService when no appointments are found for tomorrow")
    void noAppointmentsTomorrow() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(appointmentRepository.findByAppointmentDateAndStatus(
                tomorrow, AppointmentStatus.CONFIRMED))
                .thenReturn(List.of());

        appointmentScheduler.sendAppointmentReminders();

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("skips appointment when user is null — does not call emailService for that entry")
    void skipsAppointmentWithNullUser() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Appointment noUser = confirmedAppointment(tomorrow);
        noUser.setUser(null);

        when(appointmentRepository.findByAppointmentDateAndStatus(
                tomorrow, AppointmentStatus.CONFIRMED))
                .thenReturn(List.of(noUser));

        appointmentScheduler.sendAppointmentReminders();

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("continues sending to remaining appointments when one email fails")
    void continuesAfterEmailFailure() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        User secondCustomer = User.builder()
                .id(UUID.randomUUID())
                .email("second@test.com")
                .firstName("Sara")
                .lastName("Jayasinghe")
                .build();

        Appointment appt1 = confirmedAppointment(tomorrow);          // email will fail
        Appointment appt2 = confirmedAppointment(tomorrow);
        appt2.setUser(secondCustomer);

        when(appointmentRepository.findByAppointmentDateAndStatus(
                tomorrow, AppointmentStatus.CONFIRMED))
                .thenReturn(List.of(appt1, appt2));

        // first call throws, second succeeds
        doThrow(new RuntimeException("SMTP timeout"))
                .doNothing()
                .when(emailService).sendAppointmentReminderEmail(
                        any(), any(), any(), any(), any());

        appointmentScheduler.sendAppointmentReminders();

        // both attempted
        verify(emailService, times(2)).sendAppointmentReminderEmail(
                any(), any(), any(), any(), any());
        // second one specifically reached with correct customer
        verify(emailService).sendAppointmentReminderEmail(
                eq("second@test.com"),
                eq("Sara Jayasinghe"),
                eq(tomorrow),
                eq("10:00 AM"),
                eq("FITTING")
        );
    }

    @Test
    @DisplayName("queries repository using tomorrow's date (LocalDate.now().plusDays(1))")
    void queriesCorrectDate() {
        LocalDate expectedTomorrow = LocalDate.now().plusDays(1);
        when(appointmentRepository.findByAppointmentDateAndStatus(any(), any()))
                .thenReturn(List.of());

        appointmentScheduler.sendAppointmentReminders();

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(appointmentRepository).findByAppointmentDateAndStatus(
                dateCaptor.capture(), eq(AppointmentStatus.CONFIRMED));

        assertThat(dateCaptor.getValue()).isEqualTo(expectedTomorrow);
    }

    @Test
    @DisplayName("passes appointment type name as plain string to emailService")
    void passesTypeNameCorrectly() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Appointment appt = confirmedAppointment(tomorrow);
        appt.setType(AppointmentType.RENTAL_PICKUP);

        when(appointmentRepository.findByAppointmentDateAndStatus(
                tomorrow, AppointmentStatus.CONFIRMED))
                .thenReturn(List.of(appt));

        appointmentScheduler.sendAppointmentReminders();

        verify(emailService).sendAppointmentReminderEmail(
                any(), any(), any(), any(), eq("RENTAL_PICKUP"));
    }
}