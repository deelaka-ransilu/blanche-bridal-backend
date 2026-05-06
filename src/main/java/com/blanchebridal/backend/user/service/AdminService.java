//package com.blanchebridal.backend.user.service;
//
//import com.blanchebridal.backend.user.dto.req.CreateUserRequest;
//import com.blanchebridal.backend.user.dto.req.CreateWalkInCustomerRequest;
//import com.blanchebridal.backend.user.dto.req.MeasurementsRequest;
//import com.blanchebridal.backend.user.dto.req.UpdateCustomerProfileRequest;
//import com.blanchebridal.backend.user.dto.res.CustomerDetailResponse;
//import com.blanchebridal.backend.user.dto.res.MeasurementsResponse;
//import com.blanchebridal.backend.user.dto.res.UserResponse;
//
//import java.util.List;
//import java.util.UUID;
//
//public interface AdminService {
//
//    // ── Superadmin manages admins ─────────────────────────────────────────
//    List<UserResponse> listAdmins();
//    UserResponse createAdmin(CreateUserRequest request);
//    UserResponse deactivateAdmin(UUID adminId);
//    UserResponse activateAdmin(UUID adminId);
//
//    // ── Admin manages employees ───────────────────────────────────────────
//    List<UserResponse> listEmployees();
//    UserResponse createEmployee(CreateUserRequest request);
//    UserResponse deactivateEmployee(UUID employeeId);
//    UserResponse activateEmployee(UUID employeeId);
//
//    // ── Admin views customers ─────────────────────────────────────────────
//    List<UserResponse> listCustomers();
//    UserResponse getCustomer(UUID customerId);
//    UserResponse activateCustomer(UUID customerId);
//    UserResponse deactivateCustomer(UUID customerId);
//
//    // ── Customers — walk-in creation ───────────────────────────────────────
//    UserResponse createWalkInCustomer(CreateWalkInCustomerRequest request);
//
//    // ── Customers — detail (user + profile + measurements) ────────────────
//    CustomerDetailResponse getCustomerDetail(UUID customerId);
//
//    // ── Customers — profile (notes + design images) ───────────────────────
//    CustomerDetailResponse updateCustomerProfile(UUID customerId, UpdateCustomerProfileRequest request);
//
//    // ── Customers — measurements ───────────────────────────────────────────
//    MeasurementsResponse addMeasurement(UUID customerId, MeasurementsRequest request, UUID recordedByAdminId);
//    MeasurementsResponse updateMeasurement(UUID customerId, UUID measurementId, MeasurementsRequest request);
//    List<MeasurementsResponse> listMeasurements(UUID customerId);
//}

package com.blanchebridal.backend.user.service;

import com.blanchebridal.backend.user.dto.req.CreateUserRequest;
import com.blanchebridal.backend.user.dto.req.CreateWalkInCustomerRequest;
import com.blanchebridal.backend.user.dto.req.MeasurementsRequest;
import com.blanchebridal.backend.user.dto.req.UpdateCustomerProfileRequest;
import com.blanchebridal.backend.user.dto.res.CustomerDetailResponse;
import com.blanchebridal.backend.user.dto.res.MeasurementsResponse;
import com.blanchebridal.backend.user.dto.res.UserResponse;

import java.util.List;
import java.util.UUID;

public interface AdminService {

    // ── Superadmin manages admins ─────────────────────────────────────────
    List<UserResponse> listAdmins();
    UserResponse createAdmin(CreateUserRequest request);
    UserResponse deactivateAdmin(UUID adminId);
    UserResponse activateAdmin(UUID adminId);

    // ── Employees ──────────────────────────────────────────────────────────
    List<UserResponse> listEmployees();
    UserResponse createEmployee(CreateUserRequest request);
    UserResponse activateEmployee(UUID employeeId);
    UserResponse deactivateEmployee(UUID employeeId);

    // ── Customers — list / activate / deactivate ───────────────────────────
    List<UserResponse> listCustomers();
    UserResponse getCustomer(UUID customerId);
    UserResponse activateCustomer(UUID customerId);
    UserResponse deactivateCustomer(UUID customerId);

    // ── Customers — walk-in creation ───────────────────────────────────────
    UserResponse createWalkInCustomer(CreateWalkInCustomerRequest request);

    // ── Customers — detail (user + profile + measurements) ────────────────
    CustomerDetailResponse getCustomerDetail(UUID customerId);

    // ── Customers — profile (notes + design images) ───────────────────────
    CustomerDetailResponse updateCustomerProfile(UUID customerId, UpdateCustomerProfileRequest request);

    // ── Customers — measurements ───────────────────────────────────────────
    MeasurementsResponse addMeasurement(UUID customerId, MeasurementsRequest request, UUID recordedByAdminId);
    MeasurementsResponse updateMeasurement(UUID customerId, UUID measurementId, MeasurementsRequest request);
    List<MeasurementsResponse> listMeasurements(UUID customerId);
}