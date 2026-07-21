package com.blanchebridal.backend.appointment.repository;

import com.blanchebridal.backend.appointment.entity.CustomDesignRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomDesignRequestRepository extends JpaRepository<CustomDesignRequest, UUID> {

    Optional<CustomDesignRequest> findByAppointment_Id(UUID appointmentId);
    // add to CustomDesignRequestRepository.java
    Optional<CustomDesignRequest> findByFirstPaymentOrder_Id(UUID orderId);
    Optional<CustomDesignRequest> findBySecondPaymentOrder_Id(UUID orderId);
}