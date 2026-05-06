package com.blanchebridal.backend.user.service;

import com.blanchebridal.backend.user.dto.req.CreateUserRequest;
import com.blanchebridal.backend.user.dto.res.UserResponse;

import java.util.List;
import java.util.UUID;

public interface AdminService {

    // ── Superadmin manages admins ─────────────────────────────────────────
    List<UserResponse> listAdmins();
    UserResponse createAdmin(CreateUserRequest request);
    UserResponse deactivateAdmin(UUID adminId);
    UserResponse activateAdmin(UUID adminId);

    // ── Admin manages employees ───────────────────────────────────────────
    List<UserResponse> listEmployees();
    UserResponse createEmployee(CreateUserRequest request);
    UserResponse deactivateEmployee(UUID employeeId);
    UserResponse activateEmployee(UUID employeeId);

    // ── Admin views customers ─────────────────────────────────────────────
    List<UserResponse> listCustomers();
    UserResponse getCustomer(UUID customerId);
    UserResponse activateCustomer(UUID customerId);
    UserResponse deactivateCustomer(UUID customerId);
}
