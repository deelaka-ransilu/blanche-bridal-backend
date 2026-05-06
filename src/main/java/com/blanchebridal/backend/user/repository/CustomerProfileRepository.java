package com.blanchebridal.backend.user.repository;

import com.blanchebridal.backend.user.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
    Optional<CustomerProfile> findByCustomerId(UUID customerId);
}