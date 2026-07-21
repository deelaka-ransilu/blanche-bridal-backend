package com.blanchebridal.backend.appointment.dto.res;

import com.blanchebridal.backend.appointment.entity.AppointmentStatus;
import com.blanchebridal.backend.appointment.entity.OccasionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Purpose-built "everything about this custom order" response for the new
// admin/customer custom-order detail pages — stitches together the
// consultation appointment, the design request itself, and both payment
// order ids in one call, rather than making the frontend assemble this from
// three separate fetches. Keyed off CustomDesignRequest.id (not
// Appointment.id) for consistency with the CustomQuote endpoints, which are
// already keyed the same way.
@Data
@Builder
public class CustomDesignRequestResponse {

    private UUID id; // CustomDesignRequest id
    private UUID appointmentId;

    private UUID userId;
    private String customerName;
    private String customerEmail;

    // ── Consultation appointment fields ─────────────────────────────────
    private LocalDate appointmentDate;
    private String timeSlot;
    private AppointmentStatus appointmentStatus;
    private String appointmentNotes;

    // ── Custom design request fields ────────────────────────────────────
    private OccasionType occasionType;
    private LocalDate occasionDate;
    private String stylePreferences;
    private List<String> referenceImages;

    // ── Payment order linkage ───────────────────────────────────────────
    private UUID firstPaymentOrderId;
    private UUID secondPaymentOrderId;

    private LocalDateTime createdAt;
}