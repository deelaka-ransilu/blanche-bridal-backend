package edu.bridalshop.backend.repository;

import edu.bridalshop.backend.entity.CustomerMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerMeasurementRepository extends JpaRepository<CustomerMeasurement, Integer> {

    // All measurements for a customer — newest first
    List<CustomerMeasurement> findAllByCustomer_PublicIdOrderByMeasuredAtDesc(String customerPublicId);

    // Single measurement by its own publicId
    Optional<CustomerMeasurement> findByPublicId(String publicId);

    // Single measurement scoped to a specific customer (prevents cross-customer access)
    Optional<CustomerMeasurement> findByPublicIdAndCustomer_PublicId(
            String measurementPublicId, String customerPublicId);
}