package com.blanchebridal.backend.product.service;

import com.blanchebridal.backend.product.dto.res.CategoryResponse;
import com.blanchebridal.backend.product.dto.req.CreateCategoryRequest;
import com.blanchebridal.backend.product.dto.req.UpdateCategoryRequest;
import com.blanchebridal.backend.product.entity.CategoryType;

import java.util.List;
import java.util.UUID;

public interface CategoryService {
    // type == null returns all active categories, same as before this change.
    List<CategoryResponse> getAllCategories(CategoryType type);
    CategoryResponse getCategoryById(UUID id);
    CategoryResponse createCategory(CreateCategoryRequest request);
    CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request);
    void deleteCategory(UUID id);
    List<CategoryResponse> getDeletedCategories();
    CategoryResponse restoreCategory(UUID id);
}