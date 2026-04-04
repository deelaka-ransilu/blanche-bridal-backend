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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    // GET /api/admin/employees
    @GetMapping("/employees")
    public ResponseEntity<Map<String, Object>> listEmployees() {
        List<UserResponse> employees = adminService.listEmployees();
        return ResponseEntity.ok(Map.of("success", true, "data", employees));
    }

    // POST /api/admin/employees
    @PostMapping("/employees")
    public ResponseEntity<Map<String, Object>> createEmployee(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse employee = adminService.createEmployee(request);
        return ResponseEntity.ok(Map.of("success", true, "data", employee));
    }

    // PUT /api/admin/employees/{employeeId}/deactivate
    @PutMapping("/employees/{employeeId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateEmployee(
            @PathVariable UUID employeeId) {
        UserResponse employee = adminService.deactivateEmployee(employeeId);
        return ResponseEntity.ok(Map.of("success", true, "data", employee));
    }

    @PutMapping("/employees/{employeeId}/activate")
    public ResponseEntity<Map<String, Object>> activateEmployee(
            @PathVariable UUID employeeId) {
        UserResponse employee = adminService.activateEmployee(employeeId);
        return ResponseEntity.ok(Map.of("success", true, "data", employee));
    }

    // GET /api/admin/customers
    @GetMapping("/customers")
    public ResponseEntity<Map<String, Object>> listCustomers() {
        List<UserResponse> customers = adminService.listCustomers();
        return ResponseEntity.ok(Map.of("success", true, "data", customers));
    }

    // GET /api/admin/customers/{customerId}
    @GetMapping("/customers/{customerId}")
    public ResponseEntity<Map<String, Object>> getCustomer(
            @PathVariable UUID customerId) {
        UserResponse customer = adminService.getCustomer(customerId);
        return ResponseEntity.ok(Map.of("success", true, "data", customer));
    }
}