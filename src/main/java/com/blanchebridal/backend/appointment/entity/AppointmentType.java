package com.blanchebridal.backend.appointment.entity;

public enum AppointmentType {
    FITTING,
    RENTAL_PICKUP, // deprecated — kept only for historical rows, no longer created
    RENTAL_FITTING,
    PURCHASE,
    CUSTOM_CONSULTATION,
    CUSTOM_FITTING
}