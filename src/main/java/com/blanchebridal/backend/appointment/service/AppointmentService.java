package com.blanchebridal.backend.appointment.service;

import com.blanchebridal.backend.appointment.dto.req.CreateAppointmentRequest;
import com.blanchebridal.backend.appointment.dto.req.CreateCustomDesignWalkInRequest;
import com.blanchebridal.backend.appointment.dto.req.RescheduleAppointmentRequest;
import com.blanchebridal.backend.appointment.dto.res.AppointmentResponse;
import com.blanchebridal.backend.appointment.dto.res.CustomDesignRequestResponse;
import com.blanchebridal.backend.appointment.dto.res.CustomOrderSummaryResponse;
import com.blanchebridal.backend.appointment.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AppointmentService {

    List<String> getAvailableSlots(LocalDate date);
    AppointmentResponse bookAppointment(CreateAppointmentRequest req, UUID userId);
    AppointmentResponse confirmAppointment(UUID id);
    AppointmentResponse cancelAppointment(UUID id, UUID requestingUserId, String role);
    AppointmentResponse rescheduleAppointment(UUID id, RescheduleAppointmentRequest req, UUID requestingUserId, String role);
    AppointmentResponse completeAppointment(UUID id);
    Page<AppointmentResponse> getAllAppointments(AppointmentStatus status, Pageable pageable);
    Page<AppointmentResponse> getMyAppointments(UUID userId, Pageable pageable);
    AppointmentResponse getAppointmentById(UUID id, UUID requestingUserId, String role);
    CustomDesignRequestResponse getCustomDesignRequestById(
            UUID customDesignRequestId, UUID requestingUserId, String role);
    // AppointmentService.java — add
    List<CustomOrderSummaryResponse> getAllCustomOrders();
    List<CustomOrderSummaryResponse> getMyCustomOrders(UUID userId);

    AppointmentResponse bookWalkInCustomDesignRequest(CreateCustomDesignWalkInRequest req, UUID callerId, String role);
}