package com.blanchebridal.backend.appointment.service;

import com.blanchebridal.backend.appointment.entity.Appointment;

public interface GoogleCalendarService {

    String createEvent(Appointment appointment);
    void updateEvent(String googleEventId, Appointment appointment);
    void deleteEvent(String googleEventId);
}