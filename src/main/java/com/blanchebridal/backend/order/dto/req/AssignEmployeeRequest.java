package com.blanchebridal.backend.order.dto.req;

import lombok.Data;

import java.util.UUID;

@Data
public class AssignEmployeeRequest {
    private UUID employeeId;
}
