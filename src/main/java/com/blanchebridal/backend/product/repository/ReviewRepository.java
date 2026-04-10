package com.blanchebridal.backend.product.repository;

import com.blanchebridal.backend.product.entity.Review;
import com.blanchebridal.backend.product.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByProductIdAndStatus(UUID productId, ReviewStatus status);

    List<Review> findByStatus(ReviewStatus status);

    boolean existsByProductIdAndUserId(UUID productId, UUID userId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.status = 'APPROVED'")
    Double findAverageRatingByProductId(@Param("productId") UUID productId);
}