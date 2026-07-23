package com.blanchebridal.backend.appointment.dto.req;

import com.blanchebridal.backend.appointment.entity.OccasionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Admin walk-in counterpart to booking a CUSTOM_CONSULTATION appointment via
// POST /api/appointments — same relationship as CreateRentalBookingRequest is
// to RentalBookingRequest. Takes an explicit customerId instead of deriving
// the user from the caller's JWT, since the caller here is staff, not the
// customer.
@Data
public class CreateCustomDesignWalkInRequest {

    @NotNull(message = "customerId is required")
    private UUID customerId;

    @NotNull(message = "appointmentDate is required")
    private LocalDate appointmentDate;

    @NotNull(message = "timeSlot is required")
    private String timeSlot;

    private String notes;

    @NotNull(message = "occasionType is required")
    private OccasionType occasionType;

    @NotNull(message = "occasionDate is required")
    private LocalDate occasionDate;

    private String stylePreferences;

    private List<String> referenceImages;
}