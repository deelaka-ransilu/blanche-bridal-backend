package com.blanchebridal.backend.product.service;

import com.blanchebridal.backend.product.dto.res.CategoryResponse;
import com.blanchebridal.backend.product.dto.req.CreateCategoryRequest;
import com.blanchebridal.backend.product.dto.req.UpdateCategoryRequest;

import java.util.List;
import java.util.UUID;

public interface CategoryService {
    List<CategoryResponse> getAllCategories();
    CategoryResponse getCategoryById(UUID id);
    CategoryResponse createCategory(CreateCategoryRequest request);
    CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request);
    void deleteCategory(UUID id);
    List<CategoryResponse> getDeletedCategories();
    CategoryResponse restoreCategory(UUID id);
}