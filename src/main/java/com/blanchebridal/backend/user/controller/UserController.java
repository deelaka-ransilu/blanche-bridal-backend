package com.blanchebridal.backend.user.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.user.dto.req.UpdateProfileRequest;
import com.blanchebridal.backend.user.dto.res.MeasurementsResponse;
import com.blanchebridal.backend.user.dto.res.UserResponse;
import com.blanchebridal.backend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
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

        UUID userId = extractUserId(authHeader);
        log.info("[User] Profile update — user: {}", userId);
        UserResponse updated = userService.updateProfile(userId, request);
        return ResponseEntity.ok(Map.of("success", true, "data", updated));
    }

    // GET /api/users/me/measurements — customer views own history
    @GetMapping("/me/measurements")
    public ResponseEntity<Map<String, Object>> getMyMeasurements(
            @RequestHeader("Authorization") String authHeader) {

        List<MeasurementsResponse> list = userService.getMeasurements(extractUserId(authHeader));
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