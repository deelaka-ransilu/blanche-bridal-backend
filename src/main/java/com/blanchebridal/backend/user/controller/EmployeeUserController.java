package com.blanchebridal.backend.user.controller;

import com.blanchebridal.backend.user.dto.res.MeasurementsResponse;
import com.blanchebridal.backend.user.service.UserService;
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
@RequestMapping("/api/employee")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
public class EmployeeUserController {

    private final UserService userService;

    @GetMapping("/customers/{customerId}/measurements")
    public ResponseEntity<Map<String, Object>> listMeasurements(@PathVariable UUID customerId) {
        List<MeasurementsResponse> res = userService.getMeasurements(customerId);
        return ResponseEntity.ok(Map.of("success", true, "data", res));
    }
}