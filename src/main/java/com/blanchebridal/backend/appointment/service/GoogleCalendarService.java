package com.blanchebridal.backend.appointment.service;

import com.blanchebridal.backend.appointment.entity.Appointment;

import java.util.UUID;

public interface GoogleCalendarService {

    String createEvent(Appointment appointment);
    void createEventAsync(UUID appointmentId);
    void updateEvent(String googleEventId, Appointment appointment);
    void deleteEvent(String googleEventId);
}