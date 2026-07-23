package com.blanchebridal.backend.appointment.dto.res;

import com.blanchebridal.backend.appointment.entity.AppointmentStatus;
import com.blanchebridal.backend.appointment.entity.AppointmentType;
import com.blanchebridal.backend.appointment.entity.OccasionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AppointmentResponse {

    private UUID id;
    private UUID userId;
    private String customerName;
    private String customerEmail;
    private UUID productId;
    private String productName;
    private LocalDate appointmentDate;
    private String timeSlot;
    private AppointmentType type;
    private AppointmentStatus status;
    private String googleEventId;
    private String notes;
    private LocalDateTime createdAt;

    // ── CUSTOM_CONSULTATION-only fields ─────────────────────────────────
    // Flattened onto AppointmentResponse rather than nested (e.g. a
    // `customDesignRequest` object) for a simpler frontend shape — these
    // are null for FITTING/RENTAL_PICKUP/PURCHASE appointments and
    // populated only when type == CUSTOM_CONSULTATION.

    private UUID customDesignRequestId;
    private OccasionType occasionType;
    private LocalDate occasionDate;
    private String stylePreferences;
    private List<String> referenceImages;
}