package com.blanchebridal.backend.appointment.service.impl;

import com.blanchebridal.backend.appointment.entity.Appointment;
import com.blanchebridal.backend.appointment.service.GoogleCalendarService;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarServiceImpl implements GoogleCalendarService {

    private final Calendar googleCalendarClient;

    @Value("${google.calendar-id}")
    private String calendarId;

    // Colombo timezone — matches your calendar timezone
    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String createEvent(Appointment appointment) {
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

    @Override
    public void updateEvent(String googleEventId, Appointment appointment) {
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

    @Override
    public void deleteEvent(String googleEventId) {
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