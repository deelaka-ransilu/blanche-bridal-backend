package com.blanchebridal.backend.product.service.impl;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.product.spec.ProductSpecification;
import com.blanchebridal.backend.product.dto.*;
import com.blanchebridal.backend.product.dto.req.CreateProductRequest;
import com.blanchebridal.backend.product.dto.req.UpdateProductRequest;
import com.blanchebridal.backend.product.dto.res.ProductDetailResponse;
import com.blanchebridal.backend.product.dto.res.ProductSummaryResponse;
import com.blanchebridal.backend.product.entity.Category;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.entity.ProductImage;
import com.blanchebridal.backend.product.repository.CategoryRepository;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.product.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Page<ProductSummaryResponse> getProducts(ProductFilters filters, Pageable pageable) {
        return productRepository
                .findAll(ProductSpecification.withFilters(filters), pageable)
                .map(this::toSummary);
    }

    @Override
    public ProductDetailResponse getProductById(UUID id) {
        return toDetail(findById(id));
    }

    @Override
    public ProductDetailResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
        return toDetail(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySlug(slugify(request.name()))) {
            throw new ConflictException("A product with this name already exists");
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category not found: " + request.categoryId()));
        }

        Product product = Product.builder()
                .name(request.name())
                .slug(slugify(request.name()))
                .description(request.description())
                .type(request.type())
                .category(category)
                .rentalPrice(request.rentalPrice())
                .purchasePrice(request.purchasePrice())
                .stock(request.stock())
                .sizes(toJson(request.sizes()))
                .isAvailable(true)
                .images(new ArrayList<>())
                .build();

        Product saved = productRepository.save(product);
        attachImages(saved, request.imageUrls());
        return toDetail(productRepository.save(saved));
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = findById(id);

        if (request.name() != null) {
            product.setName(request.name());
            product.setSlug(slugify(request.name()));
        }
        if (request.description() != null) product.setDescription(request.description());
        if (request.type()        != null) product.setType(request.type());
        if (request.rentalPrice() != null) product.setRentalPrice(request.rentalPrice());
        if (request.purchasePrice() != null) product.setPurchasePrice(request.purchasePrice());
        if (request.stock()       != null) product.setStock(request.stock());
        if (request.isAvailable() != null) product.setIsAvailable(request.isAvailable());
        if (request.sizes()       != null) product.setSizes(toJson(request.sizes()));

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category not found: " + request.categoryId()));
            product.setCategory(category);
        }

        if (request.imageUrls() != null) {
            product.getImages().clear();
            attachImages(product, request.imageUrls());
        }

        return toDetail(productRepository.save(product));
    }

    @Override
    @Transactional
    public void deleteProduct(UUID id) {
        findById(id);
        productRepository.deleteById(id);
    }

    @Override
    @Transactional
    public ProductDetailResponse updateStock(UUID id, int quantity) {
        Product product = findById(id);
        product.setStock(quantity);
        return toDetail(productRepository.save(product));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Product findById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private void attachImages(Product product, List<String> urls) {
        if (urls == null || urls.isEmpty()) return;
        AtomicInteger order = new AtomicInteger(0);
        urls.forEach(url -> {
            ProductImage image = ProductImage.builder()
                    .product(product)
                    .url(url)
                    .displayOrder(order.getAndIncrement())
                    .build();
            product.getImages().add(image);
        });
    }

    private String slugify(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }

    private String toJson(List<String> sizes) {
        if (sizes == null || sizes.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(sizes);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private ProductSummaryResponse toSummary(Product p) {
        String firstImage = (p.getImages() != null && !p.getImages().isEmpty())
                ? p.getImages().get(0).getUrl() : null;

        ProductSummaryResponse.CategoryInfo categoryInfo = p.getCategory() != null
                ? new ProductSummaryResponse.CategoryInfo(
                p.getCategory().getId(), p.getCategory().getName())
                : null;

        return new ProductSummaryResponse(
                p.getId(), p.getName(), p.getSlug(), p.getType(),
                p.getRentalPrice(), p.getPurchasePrice(),
                p.getStock(), p.getIsAvailable(),
                firstImage, null, categoryInfo
        );
    }

    private ProductDetailResponse toDetail(Product p) {
        List<ProductDetailResponse.ImageInfo> images = p.getImages() == null
                ? Collections.emptyList()
                : p.getImages().stream()
                .map(i -> new ProductDetailResponse.ImageInfo(
                        i.getId(), i.getUrl(), i.getDisplayOrder()))
                .toList();

        ProductSummaryResponse.CategoryInfo categoryInfo = p.getCategory() != null
                ? new ProductSummaryResponse.CategoryInfo(
                p.getCategory().getId(), p.getCategory().getName())
                : null;

        return new ProductDetailResponse(
                p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getType(),
                p.getRentalPrice(), p.getPurchasePrice(),
                p.getStock(), p.getIsAvailable(),
                fromJson(p.getSizes()), images,
                null, categoryInfo,
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}