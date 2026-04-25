package com.blanchebridal.backend.appointment.controller;

import com.blanchebridal.backend.appointment.dto.req.CreateAppointmentRequest;
import com.blanchebridal.backend.appointment.dto.req.RescheduleAppointmentRequest;
import com.blanchebridal.backend.appointment.entity.AppointmentStatus;
import com.blanchebridal.backend.appointment.service.AppointmentService;
import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final JwtUtil jwtUtil;

    // PUBLIC — available slots for a date
    @GetMapping("/slots")
    public ResponseEntity<Map<String, Object>> getAvailableSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", appointmentService.getAvailableSlots(date)
        ));
    }

    // ADMIN + EMPLOYEE — all appointments
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getAllAppointments(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "appointmentDate"));
        var result = appointmentService.getAllAppointments(status, pageable);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.getContent(),
                "pagination", Map.of(
                        "page", result.getNumber(),
                        "size", result.getSize(),
                        "total", result.getTotalElements(),
                        "totalPages", result.getTotalPages()
                )
        ));
    }

    // CUSTOMER — own appointments
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> getMyAppointments(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = extractUserId(authHeader);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "appointmentDate"));
        var result = appointmentService.getMyAppointments(userId, pageable);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.getContent(),
                "pagination", Map.of(
                        "page", result.getNumber(),
                        "size", result.getSize(),
                        "total", result.getTotalElements(),
                        "totalPages", result.getTotalPages()
                )
        ));
    }

    // ALL AUTH — appointment detail
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getAppointmentById(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = extractUserId(authHeader);
        String role = extractRole();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", appointmentService.getAppointmentById(id, userId, role)
        ));
    }

    // CUSTOMER — book appointment
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> bookAppointment(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateAppointmentRequest request) {

        UUID userId = extractUserId(authHeader);
        log.info("[Appointment] Booking request — user: {}, date: {}, slot: {}, type: {}",
                userId, request.getAppointmentDate(), request.getTimeSlot(), request.getType());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", appointmentService.bookAppointment(request, userId)
        ));
    }

    // ADMIN — confirm + Google Calendar sync
    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> confirmAppointment(
            @PathVariable UUID id) {

        log.info("[Appointment] Confirming appointment {}", id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", appointmentService.confirmAppointment(id)
        ));
    }

    // ALL AUTH — cancel (customer = own only, admin = any)
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> cancelAppointment(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = extractUserId(authHeader);
        String role = extractRole();
        log.info("[Appointment] Cancel request — appointment: {}, requester: {}, role: {}",
                id, userId, role);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", appointmentService.cancelAppointment(id, userId, role)
        ));
    }

    // CUSTOMER — reschedule own appointment
    @PutMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> rescheduleAppointment(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody RescheduleAppointmentRequest request) {

        UUID userId = extractUserId(authHeader);
        String role = extractRole();
        log.info("[Appointment] Reschedule request — appointment: {}, new date: {}, slot: {}",
                id, request.getAppointmentDate(), request.getTimeSlot());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", appointmentService.rescheduleAppointment(id, request, userId, role)
        ));
    }

    // ADMIN + EMPLOYEE — mark completed
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> completeAppointment(
            @PathVariable UUID id) {

        log.info("[Appointment] Completing appointment {}", id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", appointmentService.completeAppointment(id)
        ));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return UUID.fromString(jwtUtil.extractUserId(authHeader.substring(7)));
    }

    private String extractRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("");
    }
}