package com.blanchebridal.backend.product.service;

import com.blanchebridal.backend.product.dto.*;
import com.blanchebridal.backend.product.dto.req.CreateProductRequest;
import com.blanchebridal.backend.product.dto.req.UpdateProductRequest;
import com.blanchebridal.backend.product.dto.res.ProductDetailResponse;
import com.blanchebridal.backend.product.dto.res.ProductSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductService {
    Page<ProductSummaryResponse> getProducts(ProductFilters filters, Pageable pageable);
    ProductDetailResponse getProductById(UUID id);
    ProductDetailResponse getProductBySlug(String slug);
    ProductDetailResponse createProduct(CreateProductRequest request);
    ProductDetailResponse updateProduct(UUID id, UpdateProductRequest request);
    void deleteProduct(UUID id);
    ProductDetailResponse updateStock(UUID id, int quantity);
}