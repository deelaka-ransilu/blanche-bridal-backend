package com.blanchebridal.backend.user.repository;

import com.blanchebridal.backend.user.entity.CustomerMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerMeasurementRepository extends JpaRepository<CustomerMeasurement, UUID> {
    List<CustomerMeasurement> findAllByCustomerIdOrderByMeasuredAtDesc(UUID customerId);
    List<CustomerMeasurement> findByCustomer_IdOrderByMeasuredAtDesc(UUID customerId);
    long countByCustomer_Id(UUID customerId);
}