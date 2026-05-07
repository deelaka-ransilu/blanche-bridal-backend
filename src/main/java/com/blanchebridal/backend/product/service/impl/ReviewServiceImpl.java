package com.blanchebridal.backend.product.service.impl;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.product.dto.req.CreateReviewRequest;
import com.blanchebridal.backend.product.dto.res.ReviewResponse;
import com.blanchebridal.backend.product.dto.res.ReviewStatsResponse;
import com.blanchebridal.backend.product.entity.Review;
import com.blanchebridal.backend.product.entity.ReviewStatus;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.product.repository.ReviewRepository;
import com.blanchebridal.backend.product.service.ReviewService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    public List<ReviewResponse> getApprovedReviews(UUID productId) {
        verifyProductExists(productId);
        return reviewRepository
                .findByProductIdAndStatus(productId, ReviewStatus.APPROVED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ReviewResponse submitReview(UUID productId, UUID userId, CreateReviewRequest request) {
        verifyProductExists(productId);

        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new ConflictException("You have already submitted a review for this product");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        var product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        Review review = Review.builder()
                .product(product)
                .user(user)
                .rating(request.rating())
                .comment(request.comment())
                .status(ReviewStatus.PENDING)
                .build();

        return toResponse(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public ReviewResponse approveReview(UUID reviewId) {
        Review review = findReviewById(reviewId);
        review.setStatus(ReviewStatus.APPROVED);
        return toResponse(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public ReviewResponse rejectReview(UUID reviewId) {
        Review review = findReviewById(reviewId);
        review.setStatus(ReviewStatus.REJECTED);
        return toResponse(reviewRepository.save(review));
    }

    @Override
    public List<ReviewResponse> getPendingReviews() {
        return reviewRepository.findByStatus(ReviewStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ReviewResponse> getReviewsByStatus(ReviewStatus status) {
        return reviewRepository.findByStatus(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ReviewStatsResponse getReviewStats() {
        Double averageRating = reviewRepository.findOverallAverageRating();
        long approvedReviews = reviewRepository.countByStatus(ReviewStatus.APPROVED);
        long pendingReviews = reviewRepository.countByStatus(ReviewStatus.PENDING);

        return new ReviewStatsResponse(
                averageRating == null ? 0.0 : averageRating,
                approvedReviews,
                pendingReviews
        );
    }

    // ─── Helpers

    private Review findReviewById(UUID id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
    }

    private void verifyProductExists(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getUser().getFirstName() + " " + review.getUser().getLastName(),
                review.getCreatedAt(),
                review.getProduct().getId(),
                review.getProduct().getName()
        );
    }
}