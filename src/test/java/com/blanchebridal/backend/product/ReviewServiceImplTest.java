package com.blanchebridal.backend.product;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.product.dto.req.CreateReviewRequest;
import com.blanchebridal.backend.product.dto.res.ReviewResponse;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.entity.Review;
import com.blanchebridal.backend.product.entity.ReviewStatus;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.product.repository.ReviewRepository;
import com.blanchebridal.backend.product.service.impl.ReviewServiceImpl;
import com.blanchebridal.backend.user.User;
import com.blanchebridal.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private UUID productId;
    private UUID userId;
    private UUID reviewId;

    private Product product;
    private User user;
    private Review pendingReview;
    private Review approvedReview;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        userId    = UUID.randomUUID();
        reviewId  = UUID.randomUUID();

        product = new Product();
        product.setId(productId);

        user = new User();
        user.setId(userId);
        user.setFirstName("Jane");
        user.setLastName("Doe");

        pendingReview = Review.builder()
                .id(reviewId)
                .product(product)
                .user(user)
                .rating(4)
                .comment("Beautiful dress!")
                .status(ReviewStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        approvedReview = Review.builder()
                .id(UUID.randomUUID())
                .product(product)
                .user(user)
                .rating(5)
                .comment("Absolutely loved it!")
                .status(ReviewStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET APPROVED REVIEWS
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getApprovedReviews: returns only APPROVED reviews for a product")
    void getApprovedReviews_returnsApprovedOnly() {
        when(productRepository.existsById(productId)).thenReturn(true);
        when(reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED))
                .thenReturn(List.of(approvedReview));

        List<ReviewResponse> result = reviewService.getApprovedReviews(productId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rating()).isEqualTo(5);
        assertThat(result.get(0).status()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(result.get(0).reviewerName()).isEqualTo("Jane Doe");
    }

    @Test
    @DisplayName("getApprovedReviews: returns empty list when no approved reviews exist")
    void getApprovedReviews_noneApproved_returnsEmpty() {
        when(productRepository.existsById(productId)).thenReturn(true);
        when(reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED))
                .thenReturn(List.of());

        List<ReviewResponse> result = reviewService.getApprovedReviews(productId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getApprovedReviews: fail — unknown productId throws ResourceNotFoundException")
    void getApprovedReviews_productNotFound_throwsException() {
        when(productRepository.existsById(productId)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.getApprovedReviews(productId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");

        verify(reviewRepository, never()).findByProductIdAndStatus(any(), any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SUBMIT REVIEW
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("submitReview: success — saves review with PENDING status")
    void submitReview_success_savedAsPending() {
        when(productRepository.existsById(productId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndUserId(productId, userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(reviewRepository.save(any(Review.class))).thenReturn(pendingReview);

        CreateReviewRequest request = new CreateReviewRequest(4, "Beautiful dress!");
        ReviewResponse result = reviewService.submitReview(productId, userId, request);

        assertThat(result.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(result.rating()).isEqualTo(4);
        assertThat(result.comment()).isEqualTo("Beautiful dress!");
        assertThat(result.reviewerName()).isEqualTo("Jane Doe");
    }

    @Test
    @DisplayName("submitReview: success — reviewer name is composed from firstName + lastName")
    void submitReview_reviewerNameComposedCorrectly() {
        user.setFirstName("Anna");
        user.setLastName("Silva");

        Review savedReview = Review.builder()
                .id(UUID.randomUUID())
                .product(product)
                .user(user)
                .rating(3)
                .comment("Nice")
                .status(ReviewStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(productRepository.existsById(productId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndUserId(productId, userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        ReviewResponse result = reviewService.submitReview(
                productId, userId, new CreateReviewRequest(3, "Nice"));

        assertThat(result.reviewerName()).isEqualTo("Anna Silva");
    }

    @Test
    @DisplayName("submitReview: fail — duplicate review throws ConflictException")
    void submitReview_duplicateReview_throwsConflict() {
        when(productRepository.existsById(productId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndUserId(productId, userId)).thenReturn(true);

        assertThatThrownBy(() ->
                reviewService.submitReview(productId, userId, new CreateReviewRequest(5, "Again")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already submitted a review");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitReview: fail — unknown productId throws ResourceNotFoundException")
    void submitReview_productNotFound_throwsException() {
        when(productRepository.existsById(productId)).thenReturn(false);

        assertThatThrownBy(() ->
                reviewService.submitReview(productId, userId, new CreateReviewRequest(4, "Nice")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitReview: fail — unknown userId throws ResourceNotFoundException")
    void submitReview_userNotFound_throwsException() {
        when(productRepository.existsById(productId)).thenReturn(true);
        when(reviewRepository.existsByProductIdAndUserId(productId, userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reviewService.submitReview(productId, userId, new CreateReviewRequest(4, "Nice")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(reviewRepository, never()).save(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // APPROVE REVIEW
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("approveReview: success — status changes to APPROVED")
    void approveReview_success_statusIsApproved() {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(pendingReview));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse result = reviewService.approveReview(reviewId);

        assertThat(result.status()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    @DisplayName("approveReview: fail — unknown reviewId throws ResourceNotFoundException")
    void approveReview_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(reviewRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.approveReview(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Review not found");

        verify(reviewRepository, never()).save(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // REJECT REVIEW
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("rejectReview: success — status changes to REJECTED")
    void rejectReview_success_statusIsRejected() {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(pendingReview));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse result = reviewService.rejectReview(reviewId);

        assertThat(result.status()).isEqualTo(ReviewStatus.REJECTED);
    }

    @Test
    @DisplayName("rejectReview: fail — unknown reviewId throws ResourceNotFoundException")
    void rejectReview_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(reviewRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.rejectReview(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Review not found");

        verify(reviewRepository, never()).save(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET PENDING REVIEWS
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getPendingReviews: returns all PENDING reviews across all products")
    void getPendingReviews_returnsPendingReviews() {
        when(reviewRepository.findByStatus(ReviewStatus.PENDING))
                .thenReturn(List.of(pendingReview));

        List<ReviewResponse> result = reviewService.getPendingReviews();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    @DisplayName("getPendingReviews: returns empty list when no reviews are pending")
    void getPendingReviews_noPending_returnsEmpty() {
        when(reviewRepository.findByStatus(ReviewStatus.PENDING)).thenReturn(List.of());

        List<ReviewResponse> result = reviewService.getPendingReviews();

        assertThat(result).isEmpty();
    }
}