package com.blanchebridal.backend.auth.repository;

import com.blanchebridal.backend.auth.entity.VerificationToken;
import com.blanchebridal.backend.auth.entity.VerificationTokenType;
import com.blanchebridal.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenAndType(String token, VerificationTokenType type);

    void deleteAllByUserAndType(User user, VerificationTokenType type);

    Optional<VerificationToken> findTopByUserAndTypeOrderByCreatedAtDesc(User user, VerificationTokenType type);
}