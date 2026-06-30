package com.blanchebridal.backend.auth.repository;

import com.blanchebridal.backend.auth.entity.RefreshToken;
import com.blanchebridal.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteAllByUser(User user);

    // Nightly cleanup — deletes expired or revoked rows to keep the table lean
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff OR r.revoked = true")
    void deleteExpiredAndRevoked(LocalDateTime cutoff);
}