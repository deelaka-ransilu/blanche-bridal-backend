package com.blanchebridal.backend.product.controller;

import com.blanchebridal.backend.product.dto.res.ReviewResponse;
import com.blanchebridal.backend.product.entity.ReviewStatus;
import com.blanchebridal.backend.product.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
public class ReviewController {

    private final ReviewService reviewService;

    // GET /api/reviews?status=PENDING|APPROVED|REJECTED
    @GetMapping
    public ResponseEntity<Map<String, Object>> getByStatus(
            @RequestParam(defaultValue = "PENDING") ReviewStatus status) {

        List<ReviewResponse> reviews = reviewService.getReviewsByStatus(status);
        return ResponseEntity.ok(Map.of("success", true, "data", reviews));
    }

    // GET /api/reviews/pending — kept for backward compatibility
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPending() {
        List<ReviewResponse> reviews = reviewService.getPendingReviews();
        return ResponseEntity.ok(Map.of("success", true, "data", reviews));
    }

    // PUT /api/reviews/{id}/approve
    @PutMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable UUID id) {
        log.info("[Review] Approve request — review: {}", id);
        return ResponseEntity.ok(Map.of("success", true,
                "data", reviewService.approveReview(id)));
    }

    // PUT /api/reviews/{id}/reject
    @PutMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable UUID id) {
        log.info("[Review] Reject request — review: {}", id);
        return ResponseEntity.ok(Map.of("success", true,
                "data", reviewService.rejectReview(id)));
    }
}