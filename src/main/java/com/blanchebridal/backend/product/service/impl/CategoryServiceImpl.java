package com.blanchebridal.backend.product.service.impl;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.product.dto.res.CategoryResponse;
import com.blanchebridal.backend.product.dto.req.UpdateCategoryRequest;
import com.blanchebridal.backend.product.dto.req.CreateCategoryRequest;
import com.blanchebridal.backend.product.entity.Category;
import com.blanchebridal.backend.product.repository.CategoryRepository;
import com.blanchebridal.backend.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByIsActiveTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CategoryResponse getCategoryById(UUID id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Slug already in use: " + request.slug());
        }

        Category parent = null;
        if (request.parentId() != null) {
            parent = findById(request.parentId());
        }

        Category category = Category.builder()
                .name(request.name())
                .slug(request.slug())
                .parent(parent)
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        Category category = findById(id);

        if (request.name() != null) category.setName(request.name());

        if (request.slug() != null && !request.slug().equals(category.getSlug())) {
            if (categoryRepository.existsBySlug(request.slug())) {
                throw new ConflictException("Slug already in use: " + request.slug());
            }
            category.setSlug(request.slug());
        }

        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new ConflictException("A category cannot be its own parent");
            }
            category.setParent(findById(request.parentId()));
        } else {
            category.setParent(null);
        }

        return toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(UUID id) {
        Category category = findById(id);
        category.setIsActive(false);
        categoryRepository.save(category);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Category findById(UUID id) {
        return categoryRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(
                c.getId(),
                c.getName(),
                c.getSlug(),
                c.getParent() != null && Boolean.TRUE.equals(c.getParent().getIsActive()) ? c.getParent().getId() : null,
                c.getParent() != null && Boolean.TRUE.equals(c.getParent().getIsActive()) ? c.getParent().getName() : null,
                c.getCreatedAt()
        );
    }
}