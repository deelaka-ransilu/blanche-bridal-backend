package com.blanchebridal.backend.product.service.impl;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.product.dto.res.CategoryResponse;
import com.blanchebridal.backend.product.dto.req.UpdateCategoryRequest;
import com.blanchebridal.backend.product.dto.req.CreateCategoryRequest;
import com.blanchebridal.backend.product.entity.Category;
import com.blanchebridal.backend.product.entity.CategoryType;
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
    public List<CategoryResponse> getAllCategories(CategoryType type) {
        List<Category> categories = type != null
                ? categoryRepository.findByTypeAndIsActiveTrue(type)
                : categoryRepository.findAllByIsActiveTrue();

        return categories.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CategoryResponse getCategoryById(UUID id) {
        return toResponse(findActiveById(id));
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Slug already in use: " + request.slug());
        }

        Category parent = null;
        if (request.parentId() != null) {
            parent = findActiveById(request.parentId());
            // A category's type has to match its parent's — otherwise a DRESS
            // category could end up nested under an ACCESSORY one (or vice
            // versa), which makes "filter by type" meaningless for children.
            if (parent.getType() != request.type()) {
                throw new ConflictException(
                        "Category type must match parent category type (" + parent.getType() + ")");
            }
        }

        Category category = Category.builder()
                .name(request.name())
                .slug(request.slug())
                .type(request.type())
                .parent(parent)
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        Category category = findActiveById(id);

        if (request.name() != null) category.setName(request.name());

        if (request.slug() != null && !request.slug().equals(category.getSlug())) {
            if (categoryRepository.existsBySlug(request.slug())) {
                throw new ConflictException("Slug already in use: " + request.slug());
            }
            category.setSlug(request.slug());
        }

        if (request.type() != null) category.setType(request.type());

        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new ConflictException("A category cannot be its own parent");
            }
            Category parent = findActiveById(request.parentId());
            if (parent.getType() != category.getType()) {
                throw new ConflictException(
                        "Category type must match parent category type (" + parent.getType() + ")");
            }
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        return toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(UUID id) {
        Category category = findActiveById(id);
        category.setIsActive(false);
        categoryRepository.save(category);
    }

    @Override
    public List<CategoryResponse> getDeletedCategories() {
        return categoryRepository.findByIsActiveFalse()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse restoreCategory(UUID id) {
        Category category = categoryRepository.findByIdAndIsActiveFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Deleted category not found: " + id));
        category.setIsActive(true);
        return toResponse(categoryRepository.save(category));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private Category findActiveById(UUID id) {
        return categoryRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(
                c.getId(),
                c.getName(),
                c.getSlug(),
                c.getType(),
                c.getParent() != null && Boolean.TRUE.equals(c.getParent().getIsActive()) ? c.getParent().getId() : null,
                c.getParent() != null && Boolean.TRUE.equals(c.getParent().getIsActive()) ? c.getParent().getName() : null,
                c.getCreatedAt()
        );
    }
}