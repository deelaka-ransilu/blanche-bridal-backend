package com.blanchebridal.backend.user;

import com.blanchebridal.backend.auth.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.user.dto.req.MeasurementsRequest;
import com.blanchebridal.backend.user.dto.req.UpdateProfileRequest;
import com.blanchebridal.backend.user.dto.res.MeasurementsResponse;
import com.blanchebridal.backend.user.dto.res.UserResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    // GET /api/users/me
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyProfile(
            @RequestHeader("Authorization") String authHeader) {
        UserResponse profile = userService.getProfile(extractUserId(authHeader));
        return ResponseEntity.ok(Map.of("success", true, "data", profile));
    }

    // PUT /api/users/me
    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateMyProfile(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse updated = userService.updateProfile(extractUserId(authHeader), request);
        return ResponseEntity.ok(Map.of("success", true, "data", updated));
    }

    // GET /api/users/me/measurements  — customer views own history
    @GetMapping("/me/measurements")
    public ResponseEntity<Map<String, Object>> getMyMeasurements(
            @RequestHeader("Authorization") String authHeader) {
        List<MeasurementsResponse> list = userService.getMeasurements(extractUserId(authHeader));
        return ResponseEntity.ok(Map.of("success", true, "data", list));
    }

    // POST /api/users/{customerId}/measurements  — admin records for a customer
    @PostMapping("/{customerId}/measurements")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> recordMeasurements(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID customerId,
            @Valid @RequestBody MeasurementsRequest request) {
        UUID adminId = extractUserId(authHeader);  // who is recording
        MeasurementsResponse saved = userService.saveMeasurements(customerId, adminId, request);
        return ResponseEntity.ok(Map.of("success", true, "data", saved));
    }

    // GET /api/users/{customerId}/measurements  — admin views customer history
    @GetMapping("/{customerId}/measurements")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> getCustomerMeasurements(
            @PathVariable UUID customerId) {
        List<MeasurementsResponse> list = userService.getMeasurements(customerId);
        return ResponseEntity.ok(Map.of("success", true, "data", list));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return UUID.fromString(jwtUtil.extractUserId(authHeader.substring(7)));
    }
}