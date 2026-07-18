package com.blanchebridal.backend.product.repository;

import com.blanchebridal.backend.product.entity.Category;
import com.blanchebridal.backend.product.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findBySlug(String slug);
    Optional<Category> findByIdAndIsActiveTrue(UUID id);
    boolean existsBySlug(String slug);
    List<Category> findByParentIsNullAndIsActiveTrue();
    List<Category> findAllByIsActiveTrue();
    List<Category> findByIsActiveFalse();
    Optional<Category> findByIdAndIsActiveFalse(UUID id);

    // Used to power Catalog (ACCESSORY) vs Rentals (DRESS) category
    // dropdowns/lists on the frontend.
    List<Category> findByTypeAndIsActiveTrue(CategoryType type);
}