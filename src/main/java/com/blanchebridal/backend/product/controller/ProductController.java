package com.blanchebridal.backend.product.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.product.dto.ProductFilters;
import com.blanchebridal.backend.product.dto.req.CreateProductRequest;
import com.blanchebridal.backend.product.dto.req.CreateReviewRequest;
import com.blanchebridal.backend.product.dto.req.UpdateProductRequest;
import com.blanchebridal.backend.product.dto.res.ProductSummaryResponse;
import com.blanchebridal.backend.product.entity.ProductType;
import com.blanchebridal.backend.product.service.ProductService;
import com.blanchebridal.backend.product.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ReviewService reviewService;
    private final JwtUtil jwtUtil;

    // ── Public ────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean available,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1
                && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParts[0]));

        ProductFilters filters = new ProductFilters(
                type, categoryId, search, minPrice, maxPrice, available);

        Page<ProductSummaryResponse> result = productService.getProducts(filters, pageable);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.getContent(),
                "pagination", Map.of(
                        "page", result.getNumber(),
                        "size", result.getSize(),
                        "total", result.getTotalElements(),
                        "totalPages", result.getTotalPages()
                )
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", productService.getProductById(id)));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<Map<String, Object>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", productService.getProductBySlug(slug)));
    }

    // ── Admin only ────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateProductRequest request) {
        log.info("[Product] Create → name: {}, type: {}", request.name(), request.type());
        return ResponseEntity.ok(Map.of("success", true,
                "data", productService.createProduct(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        log.info("[Product] Update → id: {}", id);
        return ResponseEntity.ok(Map.of("success", true,
                "data", productService.updateProduct(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id) {
        log.info("[Product] Deactivate → id: {}", id);
        productService.deleteProduct(id);
        return ResponseEntity.ok(Map.of("success", true, "data", "Product deactivated"));
    }

    @PutMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> updateStock(
            @PathVariable UUID id,
            @RequestParam int quantity) {
        log.info("[Product] Stock update → id: {}, quantity: {}", id, quantity);
        return ResponseEntity.ok(Map.of("success", true,
                "data", productService.updateStock(id, quantity)));
    }

    // ── NEW: GET /api/products/deleted ────────────────────────────────────────
    @GetMapping("/deleted")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> getDeleted() {
        log.info("[Product] Fetching deleted products");
        List<ProductSummaryResponse> deleted = productService.getDeletedProducts();
        return ResponseEntity.ok(Map.of("success", true, "data", deleted));
    }

    // ── NEW: PUT /api/products/{id}/restore ───────────────────────────────────
    @PutMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable UUID id) {
        log.info("[Product] Restore → id: {}", id);
        return ResponseEntity.ok(Map.of("success", true,
                "data", productService.restoreProduct(id)));
    }

    // ── Public: reviews ───────────────────────────────────────────────────────

    @GetMapping("/{id}/reviews")
    public ResponseEntity<Map<String, Object>> getReviews(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", reviewService.getApprovedReviews(id)));
    }

    @PostMapping("/{id}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Object>> submitReview(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateReviewRequest request) {
        UUID userId = extractUserId(authHeader);
        log.info("[Product] Review → product: {}, user: {}, rating: {}",
                id, userId, request.rating());
        return ResponseEntity.ok(Map.of("success", true,
                "data", reviewService.submitReview(id, userId, request)));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> deleteImage(
            @PathVariable UUID id,
            @PathVariable UUID imageId) {
        log.info("[Product] Image delete → product: {}, image: {}", id, imageId);
        productService.deleteProductImage(id, imageId);
        return ResponseEntity.ok(Map.of("success", true, "data", "Image removed"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        return UUID.fromString(jwtUtil.extractUserId(authHeader.substring(7)));
    }
}