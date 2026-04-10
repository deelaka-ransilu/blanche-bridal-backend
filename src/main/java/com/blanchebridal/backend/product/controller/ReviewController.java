package com.blanchebridal.backend.product.controller;

import com.blanchebridal.backend.product.dto.res.ReviewResponse;
import com.blanchebridal.backend.product.service.ReviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    private final ReviewService reviewService;

    // GET /api/reviews/pending — all pending reviews
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPending() {
        List<ReviewResponse> reviews = reviewService.getPendingReviews();
        return ResponseEntity.ok(Map.of("success", true, "data", reviews));
    }

    // PUT /api/reviews/{id}/approve
    @PutMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", reviewService.approveReview(id)));
    }

    // PUT /api/reviews/{id}/reject
    @PutMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", reviewService.rejectReview(id)));
    }
}