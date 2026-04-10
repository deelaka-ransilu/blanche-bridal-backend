package com.blanchebridal.backend.product.controller;

import com.blanchebridal.backend.product.service.ProductService;
import com.blanchebridal.backend.product.entity.ProductType;
import com.blanchebridal.backend.product.dto.*;
import com.blanchebridal.backend.product.dto.req.CreateProductRequest;
import com.blanchebridal.backend.product.dto.req.UpdateProductRequest;
import com.blanchebridal.backend.product.dto.res.ProductSummaryResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // Public
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

    // Public
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", productService.getProductById(id)));
    }

    // Public
    @GetMapping("/slug/{slug}")
    public ResponseEntity<Map<String, Object>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", productService.getProductBySlug(slug)));
    }

    // Admin only
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", productService.createProduct(request)));
    }

    // Admin only
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", productService.updateProduct(id, request)));
    }

    // Admin only
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(Map.of("success", true, "data", "Product deleted"));
    }

    // Admin only
    @PutMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> updateStock(
            @PathVariable UUID id,
            @RequestParam int quantity) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", productService.updateStock(id, quantity)));
    }
}