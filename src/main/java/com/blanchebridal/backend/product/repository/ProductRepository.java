package com.blanchebridal.backend.product.repository;

import com.blanchebridal.backend.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>,
        JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlugAndIsActiveTrue(String slug);
    Optional<Product> findByIdAndIsActiveTrue(UUID id);
    Page<Product> findAllByIsActiveTrue(Pageable pageable);
    boolean existsBySlug(String slug);
    Page<Product> findByCategoryId(UUID categoryId, Pageable pageable);
}