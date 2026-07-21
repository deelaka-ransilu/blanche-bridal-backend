package com.blanchebridal.backend.appointment.controller;

import com.blanchebridal.backend.appointment.service.AppointmentService;
import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

// Purpose-built "everything about this custom order" read endpoint — see
// CustomDesignRequestResponse for why this exists as its own controller
// rather than being folded into AppointmentController. Keyed off
// CustomDesignRequest id, consistent with CustomQuoteController's endpoints.
@Slf4j
@RestController
@RequestMapping("/api/custom-design-requests")
@RequiredArgsConstructor
public class CustomDesignRequestController {

    private final AppointmentService appointmentService;
    private final JwtUtil jwtUtil;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getCustomDesignRequestById(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = extractUserId(authHeader);
        String role = extractRole();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", appointmentService.getCustomDesignRequestById(id, userId, role)
        ));
    }

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