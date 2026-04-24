package com.blanchebridal.backend.appointment.scheduler;

import com.blanchebridal.backend.appointment.entity.Appointment;
import com.blanchebridal.backend.appointment.entity.AppointmentStatus;
import com.blanchebridal.backend.appointment.repository.AppointmentRepository;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentScheduler {

    private final AppointmentRepository appointmentRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 9 * * *")
    public void sendAppointmentReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        List<Appointment> tomorrowsAppointments = appointmentRepository
                .findByAppointmentDateAndStatus(tomorrow, AppointmentStatus.CONFIRMED);

        int sent = 0;
        for (Appointment appt : tomorrowsAppointments) {
            try {
                User customer = appt.getUser();
                if (customer != null) {
                    emailService.sendAppointmentReminderEmail(
                            customer.getEmail(),
                            customer.getFirstName() + " " + customer.getLastName(),
                            appt.getAppointmentDate(),
                            appt.getTimeSlot(),
                            appt.getType().name()
                    );
                    sent++;
                }
            } catch (Exception e) {
                log.warn("Failed to send reminder for appointment {}: {}",
                        appt.getId(), e.getMessage());
            }
        }

        log.info("[AppointmentScheduler] Sent {} reminder email(s) for {}", sent, tomorrow);
    }
}