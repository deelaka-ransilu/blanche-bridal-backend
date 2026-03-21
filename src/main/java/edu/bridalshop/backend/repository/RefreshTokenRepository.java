package edu.bridalshop.backend.repository;

import edu.bridalshop.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {

    Optional<RefreshToken> findByToken(String token);

    // Revoke all active tokens for a user (used on logout)
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.userId = :userId AND rt.revoked = false")
    void revokeAllUserTokens(@Param("userId") Integer userId);

    // Delete expired + revoked tokens (cleanup job later)
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.userId = :userId")
    void deleteAllByUserId(@Param("userId") Integer userId);
}