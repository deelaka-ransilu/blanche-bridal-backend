package com.blanchebridal.backend.order.security;

import com.blanchebridal.backend.order.entity.ProductionStageRecord;
import com.blanchebridal.backend.order.repository.ProductionStageRecordRepository;
import com.blanchebridal.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("productionSecurity")
@RequiredArgsConstructor
public class ProductionSecurityService {

    private final ProductionStageRecordRepository recordRepository;

    public boolean isAssignedEmployee(UUID orderId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return false;
        }

        User principal = (User) authentication.getPrincipal();

        return recordRepository.findByOrderId(orderId)
                .map(ProductionStageRecord::getAssignedEmployee)
                .map(employee -> employee.getId().equals(principal.getId()))
                .orElse(false);
    }
}