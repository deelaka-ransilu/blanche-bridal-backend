package edu.bridalshop.backend.repository;

import edu.bridalshop.backend.entity.EmployeeProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile, Integer> {

    Optional<EmployeeProfile> findByUser_UserId(Integer userId);

    Optional<EmployeeProfile> findByUser_PublicId(String publicId);
}