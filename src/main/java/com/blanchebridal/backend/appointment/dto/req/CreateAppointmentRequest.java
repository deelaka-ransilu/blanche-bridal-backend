package com.blanchebridal.backend.appointment.dto.req;

import com.blanchebridal.backend.appointment.entity.AppointmentType;
import com.blanchebridal.backend.appointment.entity.OccasionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateAppointmentRequest {

    private UUID productId; // optional

    @NotNull(message = "Appointment date is required")
    private LocalDate appointmentDate;

    @NotNull(message = "Time slot is required")
    private String timeSlot;

    @NotNull(message = "Appointment type is required")
    private AppointmentType type;

    private String notes;

    // ── CUSTOM_CONSULTATION-only fields ─────────────────────────────────
    // All optional at the DTO/validation level (no @NotNull) because they
    // are meaningless for FITTING/RENTAL_PICKUP/PURCHASE. Required-ness is
    // enforced in AppointmentServiceImpl.bookAppointment() only when
    // type == CUSTOM_CONSULTATION, same way discount validation in
    // OrderServiceImpl is conditional on staff/discount-type rather than
    // expressed as a blanket bean-validation annotation.

    private OccasionType occasionType;

    private LocalDate occasionDate;

    private String stylePreferences;

    // Cloudinary URLs, same list-of-strings shape as ProductImageInput
    // conceptually, but flattened here since these are just plain URLs, no
    // per-image metadata (displayOrder etc) needed.
    private List<String> referenceImages;
}