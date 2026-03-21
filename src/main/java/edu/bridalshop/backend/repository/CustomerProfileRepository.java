package edu.bridalshop.backend.repository;

import edu.bridalshop.backend.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Integer> {

    Optional<CustomerProfile> findByUser_UserId(Integer userId);

    Optional<CustomerProfile> findByUser_PublicId(String publicId);
}