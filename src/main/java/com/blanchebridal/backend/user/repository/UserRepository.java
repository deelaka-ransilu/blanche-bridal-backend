package com.blanchebridal.backend.user.repository;

import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    List<User> findByRole(UserRole role);

    boolean existsByEmailAndStatusNot(String email, UserStatus status);

    boolean existsByPhoneAndStatusNot(String phone, UserStatus status);
}