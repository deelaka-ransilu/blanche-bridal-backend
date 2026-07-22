package com.blanchebridal.backend.appointment.repository;

import com.blanchebridal.backend.appointment.entity.CustomDesignRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomDesignRequestRepository extends JpaRepository<CustomDesignRequest, UUID> {

    Optional<CustomDesignRequest> findByAppointment_Id(UUID appointmentId);
    Optional<CustomDesignRequest> findByFirstPaymentOrder_Id(UUID orderId);
    Optional<CustomDesignRequest> findBySecondPaymentOrder_Id(UUID orderId);

    List<CustomDesignRequest> findByFirstPaymentOrderIsNotNullOrderByCreatedAtDesc();
    List<CustomDesignRequest> findAllByOrderByCreatedAtDesc();

    // NEW — backs the customer's own /my/orders "Custom" tab
    List<CustomDesignRequest> findByAppointment_User_IdOrderByCreatedAtDesc(UUID userId);
}