package com.blanchebridal.backend.user.controller;

import com.blanchebridal.backend.user.dto.req.CreateUserRequest;
import com.blanchebridal.backend.user.dto.res.UserResponse;
import com.blanchebridal.backend.user.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
public class SuperadminController {

    private final AdminService adminService;

    // GET /api/superadmin/admins
    @GetMapping("/admins")
    public ResponseEntity<Map<String, Object>> listAdmins() {
        List<UserResponse> admins = adminService.listAdmins();
        return ResponseEntity.ok(Map.of("success", true, "data", admins));
    }

    // POST /api/superadmin/admins
    @PostMapping("/admins")
    public ResponseEntity<Map<String, Object>> createAdmin(
            @Valid @RequestBody CreateUserRequest request) {

        log.info("[Superadmin] Create admin — email: {}", request.email());
        UserResponse admin = adminService.createAdmin(request);
        return ResponseEntity.ok(Map.of("success", true, "data", admin));
    }

    // PUT /api/superadmin/admins/{adminId}/deactivate
    @PutMapping("/admins/{adminId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateAdmin(
            @PathVariable UUID adminId) {

        log.info("[Superadmin] Deactivate admin — id: {}", adminId);
        UserResponse admin = adminService.deactivateAdmin(adminId);
        return ResponseEntity.ok(Map.of("success", true, "data", admin));
    }

    // PUT /api/superadmin/admins/{adminId}/activate
    @PutMapping("/admins/{adminId}/activate")
    public ResponseEntity<Map<String, Object>> activateAdmin(
            @PathVariable UUID adminId) {

        log.info("[Superadmin] Activate admin — id: {}", adminId);
        UserResponse admin = adminService.activateAdmin(adminId);
        return ResponseEntity.ok(Map.of("success", true, "data", admin));
    }
}