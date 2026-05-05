package com.blanchebridal.backend.product.repository;

import com.blanchebridal.backend.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>,
        JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndIsActiveTrue(UUID id);

    Optional<Product> findBySlugAndIsActiveTrue(String slug);

    boolean existsBySlug(String slug);

    // ── NEW ───────────────────────────────────────────────────────────────────

    // Used by getDeletedProducts()
    List<Product> findByIsActiveFalse();

    // Used by restoreProduct()
    Optional<Product> findByIdAndIsActiveFalse(UUID id);
}