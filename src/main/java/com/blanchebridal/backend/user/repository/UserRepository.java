package com.blanchebridal.backend.user.repository;

import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRole(UserRole role);
    List<User> findAllByRole(UserRole role);
    Optional<User> findByIdAndRole(UUID id, UserRole role);
}