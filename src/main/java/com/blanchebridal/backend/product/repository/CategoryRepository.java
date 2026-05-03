package com.blanchebridal.backend.product.repository;

import com.blanchebridal.backend.product.entity.Category;
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
}