package com.blanchebridal.backend.appointment.service.impl;

import com.blanchebridal.backend.appointment.entity.Appointment;
import com.blanchebridal.backend.appointment.repository.AppointmentRepository;
import com.blanchebridal.backend.appointment.service.GoogleCalendarService;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class GoogleCalendarServiceImpl implements GoogleCalendarService {

    @Autowired(required = false)
    private Calendar googleCalendarClient;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Value("${google.calendar-id}")
    private String calendarId;

    // Colombo timezone — matches your calendar timezone
    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    // Synchronous — kept as-is so it can still be called directly and
    // return the real event id immediately (e.g. from createEventAsync
    // below, or from any future caller that genuinely needs to block on
    // the result). NOT annotated @Async: an @Async method with a non-void,
    // non-Future return type silently discards its return value, which
    // would make eventId always come back null to any synchronous caller.
    @Override
    public String createEvent(Appointment appointment) {
        if (googleCalendarClient == null) {
            log.warn("[GoogleCalendar] Disabled — skipping event creation");
            return null;
        }
        try {
            Event event = buildEvent(appointment);

            Event created = googleCalendarClient
                    .events()
                    .insert(calendarId, event)
                    .execute();

            log.info("[GoogleCalendar] Event created: {} for appointment {}",
                    created.getId(), appointment.getId());
            return created.getId();

        } catch (Exception e) {
            log.error("[GoogleCalendar] Failed to create event for appointment {}: {}",
                    appointment.getId(), e.getMessage());
            // Don't fail the whole confirm — just return null
            return null;
        }
    }

    // Fire-and-forget wrapper used by AppointmentServiceImpl.confirmAppointment
    // so the calendar API round-trip (which can take several seconds) no
    // longer blocks the confirm request/response cycle. Runs on a separate
    // thread — can't share the caller's transaction or in-memory entity, so
    // it does its own repository lookup and save once the event is created.
    @Async
    @Override
    public void createEventAsync(UUID appointmentId) {
        appointmentRepository.findById(appointmentId).ifPresent(appointment -> {
            String eventId = createEvent(appointment);
            if (eventId != null) {
                appointment.setGoogleEventId(eventId);
                appointmentRepository.save(appointment);
            }
        });
    }

    // Async — void return, no caller depends on its result synchronously
    // (rescheduleAppointment just fires and moves on), so this is safe to
    // mark @Async directly without a wrapper.
    @Async
    @Override
    public void updateEvent(String googleEventId, Appointment appointment) {
        if (googleCalendarClient == null) { return; }
        try {
            Event existing = googleCalendarClient
                    .events()
                    .get(calendarId, googleEventId)
                    .execute();

            EventDateTime start = toEventDateTime(
                    appointment.getAppointmentDate().atTime(
                            parseHour(appointment.getTimeSlot()),
                            parseMinute(appointment.getTimeSlot())));

            EventDateTime end = toEventDateTime(
                    appointment.getAppointmentDate().atTime(
                            parseHour(appointment.getTimeSlot()),
                            parseMinute(appointment.getTimeSlot())).plusHours(1));

            existing.setStart(start);
            existing.setEnd(end);

            googleCalendarClient
                    .events()
                    .update(calendarId, googleEventId, existing)
                    .execute();

            log.info("[GoogleCalendar] Event updated: {} for appointment {}",
                    googleEventId, appointment.getId());

        } catch (Exception e) {
            log.error("[GoogleCalendar] Failed to update event {} : {}",
                    googleEventId, e.getMessage());
        }
    }

    // Async — same reasoning as updateEvent above.
    @Async
    @Override
    public void deleteEvent(String googleEventId) {
        if (googleCalendarClient == null) { return; }
        try {
            googleCalendarClient
                    .events()
                    .delete(calendarId, googleEventId)
                    .execute();

            log.info("[GoogleCalendar] Event deleted: {}", googleEventId);

        } catch (Exception e) {
            // Event may already be gone — log and swallow
            log.warn("[GoogleCalendar] Failed to delete event {} : {}",
                    googleEventId, e.getMessage());
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Event buildEvent(Appointment appointment) {
        String customerName = appointment.getUser() != null
                ? appointment.getUser().getFirstName() + " "
                  + appointment.getUser().getLastName()
                : "Customer";

        String customerEmail = appointment.getUser() != null
                ? appointment.getUser().getEmail()
                : null;

        String productName = appointment.getProduct() != null
                ? appointment.getProduct().getName()
                : "No specific dress selected";

        String summary = appointment.getType() + " — " + customerName;

        String description = "Product: " + productName
                + (appointment.getNotes() != null
                ? "\nNotes: " + appointment.getNotes()
                : "");

        int hour = parseHour(appointment.getTimeSlot());
        int minute = parseMinute(appointment.getTimeSlot());

        LocalDateTime startLdt = appointment.getAppointmentDate().atTime(hour, minute);
        LocalDateTime endLdt = startLdt.plusHours(1);

        EventDateTime start = toEventDateTime(startLdt);
        EventDateTime end = toEventDateTime(endLdt);

        Event event = new Event()
                .setSummary(summary)
                .setDescription(description)
                .setStart(start)
                .setEnd(end);

        return event;
    }

    private EventDateTime toEventDateTime(LocalDateTime ldt) {
        ZonedDateTime zdt = ldt.atZone(ZONE);
        DateTime dt = new DateTime(zdt.toInstant().toEpochMilli());
        return new EventDateTime()
                .setDateTime(dt)
                .setTimeZone(ZONE.getId());
    }

    private int parseHour(String timeSlot) {
        return Integer.parseInt(timeSlot.split(":")[0]);
    }

    private int parseMinute(String timeSlot) {
        return Integer.parseInt(timeSlot.split(":")[1]);
    }
}