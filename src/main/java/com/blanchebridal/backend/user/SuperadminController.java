package com.blanchebridal.backend.user;

import com.blanchebridal.backend.user.dto.req.CreateUserRequest;
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
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@SecurityRequirement(name = "bearerAuth")
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
        UserResponse admin = adminService.createAdmin(request);
        return ResponseEntity.ok(Map.of("success", true, "data", admin));
    }

    // PUT /api/superadmin/admins/{adminId}/deactivate
    @PutMapping("/admins/{adminId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateAdmin(
            @PathVariable UUID adminId) {
        UserResponse admin = adminService.deactivateAdmin(adminId);
        return ResponseEntity.ok(Map.of("success", true, "data", admin));
    }

    // PUT /api/superadmin/admins/{adminId}/activate
    @PutMapping("/admins/{adminId}/activate")
    public ResponseEntity<Map<String, Object>> activateAdmin(
            @PathVariable UUID adminId) {
        UserResponse admin = adminService.activateAdmin(adminId);
        return ResponseEntity.ok(Map.of("success", true, "data", admin));
    }
}