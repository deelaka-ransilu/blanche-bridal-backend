package com.blanchebridal.backend.product.repository;

import com.blanchebridal.backend.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

}