package com.blanchebridal.backend.product.controller;

import com.blanchebridal.backend.product.dto.req.CreateCategoryRequest;
import com.blanchebridal.backend.product.dto.req.UpdateCategoryRequest;
import com.blanchebridal.backend.product.dto.res.CategoryResponse;
import com.blanchebridal.backend.product.service.CategoryService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // Public
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        List<CategoryResponse> categories = categoryService.getAllCategories();
        return ResponseEntity.ok(Map.of("success", true, "data", categories));
    }

    // Public
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("success", true,
                "data", categoryService.getCategoryById(id)));
    }

    // Admin only
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> create(
            @Valid @RequestBody CreateCategoryRequest request) {

        log.info("[Category] Create request — name: {}", request.name());
        return ResponseEntity.ok(Map.of("success", true,
                "data", categoryService.createCategory(request)));
    }

    // Admin only
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {

        log.info("[Category] Update request — id: {}", id);
        return ResponseEntity.ok(Map.of("success", true,
                "data", categoryService.updateCategory(id, request)));
    }

    // Admin only
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id) {
        log.info("[Category] Delete request — id: {}", id);
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(Map.of("success", true, "data", "Category deleted"));
    }
}