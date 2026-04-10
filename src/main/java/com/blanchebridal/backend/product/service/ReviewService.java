package com.blanchebridal.backend.product.service;

import com.blanchebridal.backend.product.dto.req.CreateReviewRequest;
import com.blanchebridal.backend.product.dto.res.ReviewResponse;

import java.util.List;
import java.util.UUID;

public interface ReviewService {
    List<ReviewResponse> getApprovedReviews(UUID productId);
    ReviewResponse submitReview(UUID productId, UUID userId, CreateReviewRequest request);
    ReviewResponse approveReview(UUID reviewId);
    ReviewResponse rejectReview(UUID reviewId);
    List<ReviewResponse> getPendingReviews();
}