package com.blanchebridal.backend.auth;

import com.blanchebridal.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenAndType(String token, VerificationTokenType type);

    // Delete all tokens for a user of a specific type
    // (e.g. before creating a new one, clean up old ones)
    void deleteAllByUserAndType(User user, VerificationTokenType type);
}