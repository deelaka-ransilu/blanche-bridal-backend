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

    @GetMapping
    public ResponseEntity<Map<String, Object>> getByStatus(
            @RequestParam(defaultValue = "PENDING") ReviewStatus status) {

        List<ReviewResponse> reviews = reviewService.getReviewsByStatus(status);
        return ResponseEntity.ok(Map.of("success", true, "data", reviews));
    }

    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPending() {
        List<ReviewResponse> reviews = reviewService.getPendingReviews();
        return ResponseEntity.ok(Map.of("success", true, "data", reviews));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable UUID id) {
        log.info("[Review] Approve request — review: {}", id);
        return ResponseEntity.ok(Map.of("success", true,
                "data", reviewService.approveReview(id)));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable UUID id) {
        log.info("[Review] Reject request — review: {}", id);
        return ResponseEntity.ok(Map.of("success", true,
                "data", reviewService.rejectReview(id)));
    }

    // GET /api/reviews/stats
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", reviewService.getReviewStats()
        ));
    }
}