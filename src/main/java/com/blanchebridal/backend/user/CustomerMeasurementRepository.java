package com.blanchebridal.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerMeasurementRepository extends JpaRepository<CustomerMeasurement, UUID> {

    // customer is a User relation — use customer.id to query by UUID
    List<CustomerMeasurement> findByCustomer_IdOrderByMeasuredAtDesc(UUID customerId);

    long countByCustomer_Id(UUID customerId);
}