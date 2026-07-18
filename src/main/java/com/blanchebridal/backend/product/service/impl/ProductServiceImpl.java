package com.blanchebridal.backend.product.service.impl;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.product.repository.ProductImageRepository;
import com.blanchebridal.backend.product.spec.ProductSpecification;
import com.blanchebridal.backend.product.dto.*;
import com.blanchebridal.backend.product.dto.req.CreateProductRequest;
import com.blanchebridal.backend.product.dto.req.ProductImageInput;
import com.blanchebridal.backend.product.dto.req.UpdateProductRequest;
import com.blanchebridal.backend.product.dto.res.ProductDetailResponse;
import com.blanchebridal.backend.product.dto.res.ProductSummaryResponse;
import com.blanchebridal.backend.product.entity.Category;
import com.blanchebridal.backend.product.entity.CategoryType;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.entity.ProductImage;
import com.blanchebridal.backend.product.entity.ProductType;
import com.blanchebridal.backend.product.repository.CategoryRepository;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.product.service.ProductService;
import com.cloudinary.Cloudinary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;
    private final ProductImageRepository productImageRepository;
    private final Cloudinary cloudinary;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getProducts(ProductFilters filters, Pageable pageable) {
        return productRepository
                .findAll(ProductSpecification.withFilters(filters), pageable)
                .map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductById(UUID id) {
        return toDetail(findActiveById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndIsActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug));
        return toDetail(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySlug(slugify(request.name()))) {
            throw new ConflictException("A product with this name already exists");
        }

        // categoryId is required — category is what determines whether this
        // product is sellable (ACCESSORY) or rentable (DRESS).
        Category category = categoryRepository.findByIdAndIsActiveTrue(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + request.categoryId()));

        validateCategoryProductTypeMatch(
                category, request.purchasePrice(), request.rentalPrice(), request.rentalPricePerDay());

        Product product = Product.builder()
                .name(request.name())
                .slug(slugify(request.name()))
                .description(request.description())
                .type(deriveProductType(category)) // derived from category, never from the request
                .category(category)
                .rentalPrice(request.rentalPrice())
                .rentalPricePerDay(request.rentalPricePerDay())
                .purchasePrice(request.purchasePrice())
                .stock(request.stock())
                .sizes(toJson(request.sizes()))
                .isAvailable(true)
                .images(new ArrayList<>())
                .build();

        Product saved = productRepository.save(product);
        attachImages(saved, request.images());
        return toDetail(productRepository.save(saved));
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = findActiveById(id);

        if (request.name() != null) {
            product.setName(request.name());
            product.setSlug(slugify(request.name()));
        }
        if (request.description()  != null) product.setDescription(request.description());
        if (request.stock()        != null) product.setStock(request.stock());
        if (request.isAvailable()  != null) product.setIsAvailable(request.isAvailable());
        if (request.sizes()        != null) product.setSizes(toJson(request.sizes()));

        // Resolve the category that will be in effect after this update —
        // either newly requested, or whatever the product already has.
        Category category = product.getCategory();
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndIsActiveTrue(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category not found: " + request.categoryId()));
        }
        if (category == null) {
            throw new ConflictException("Product must belong to a category");
        }

        // Resolve the price fields that will be in effect after this update.
        // Same "null means unchanged" convention as every other field above —
        // to clear a price when switching category type, the caller must
        // explicitly send the correct fields for the new type.
        BigDecimal purchasePrice = request.purchasePrice() != null
                ? request.purchasePrice() : product.getPurchasePrice();
        BigDecimal rentalPrice = request.rentalPrice() != null
                ? request.rentalPrice() : product.getRentalPrice();
        BigDecimal rentalPricePerDay = request.rentalPricePerDay() != null
                ? request.rentalPricePerDay() : product.getRentalPricePerDay();

        validateCategoryProductTypeMatch(category, purchasePrice, rentalPrice, rentalPricePerDay);

        product.setCategory(category);
        product.setType(deriveProductType(category));
        product.setPurchasePrice(purchasePrice);
        product.setRentalPrice(rentalPrice);
        product.setRentalPricePerDay(rentalPricePerDay);

        if (request.images() != null) {
            // Clean up old images from Cloudinary before replacing
            product.getImages().forEach(this::destroyOnCloudinaryQuietly);
            product.getImages().clear();
            attachImages(product, request.images());
        }

        return toDetail(productRepository.save(product));
    }

    @Override
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findActiveById(id);
        product.setIsActive(false);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse updateStock(UUID id, int quantity) {
        Product product = findActiveById(id);
        product.setStock(quantity);
        return toDetail(productRepository.save(product));
    }

    @Override
    @Transactional
    public void deleteProductImage(UUID productId, UUID imageId) {
        findActiveById(productId);

        ProductImage image = productImageRepository.findByIdAndIsActiveTrue(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));

        if (!image.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Image not found on this product");
        }

        destroyOnCloudinaryQuietly(image);

        image.setIsActive(false);
        productImageRepository.save(image);
    }

    // ── NEW: get all deleted (inactive) products ──────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> getDeletedProducts() {
        return productRepository.findByIsActiveFalse()
                .stream()
                .map(this::toSummary)
                .toList();
    }

    // ── NEW: restore a deleted product ────────────────────────────────────────
    @Override
    @Transactional
    public ProductDetailResponse restoreProduct(UUID id) {
        Product product = productRepository.findByIdAndIsActiveFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Deleted product not found: " + id));
        product.setIsActive(true);
        return toDetail(productRepository.save(product));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private Product findActiveById(UUID id) {
        return productRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    // A category's type fully determines whether a product is sellable
    // (ACCESSORY) or rentable (DRESS) — Product.type is never trusted from
    // the client, only ever derived here.
    private ProductType deriveProductType(Category category) {
        return category.getType() == CategoryType.DRESS ? ProductType.DRESS : ProductType.ACCESSORY;
    }

    // ACCESSORY categories: purchase-only, no rental pricing allowed.
    // DRESS categories: rental-only (flat and/or per-day), no purchase price.
    // "Rentable" counts as either rentalPrice or rentalPricePerDay being set,
    // since RentalServiceImpl treats rentalPricePerDay as valid rentable
    // state on its own (used instead of the flat rentalPrice when present).
    private void validateCategoryProductTypeMatch(
            Category category,
            BigDecimal purchasePrice,
            BigDecimal rentalPrice,
            BigDecimal rentalPricePerDay
    ) {
        boolean hasPurchase = purchasePrice != null;
        boolean hasRental = rentalPrice != null || rentalPricePerDay != null;

        if (category.getType() == CategoryType.ACCESSORY) {
            if (!hasPurchase) {
                throw new ConflictException("Accessory products require a purchase price");
            }
            if (hasRental) {
                throw new ConflictException("Accessory products cannot have rental pricing");
            }
        } else { // DRESS
            if (!hasRental) {
                throw new ConflictException("Dress products require a rental price or per-day rate");
            }
            if (hasPurchase) {
                throw new ConflictException("Dress products cannot have a purchase price");
            }
        }
    }

    private void attachImages(Product product, List<ProductImageInput> images) {
        if (images == null || images.isEmpty()) return;
        AtomicInteger order = new AtomicInteger(0);
        images.forEach(img -> {
            ProductImage image = ProductImage.builder()
                    .product(product)
                    .url(img.url())
                    .publicId(img.publicId())
                    .displayOrder(order.getAndIncrement())
                    .build();
            product.getImages().add(image);
        });
    }

    private void destroyOnCloudinaryQuietly(ProductImage image) {
        if (image.getPublicId() == null || image.getPublicId().isBlank()) return;
        try {
            cloudinary.uploader().destroy(image.getPublicId(), Map.of());
        } catch (IOException e) {
            // Don't fail the DB operation over a Cloudinary cleanup failure —
            // log and move on, orphaned file can be cleaned up manually if needed.
            log.warn("[Product] Cloudinary destroy failed for publicId {}: {}",
                    image.getPublicId(), e.getMessage());
        }
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
                ? p.getImages().getFirst().getUrl() : null;

        ProductSummaryResponse.CategoryInfo categoryInfo = p.getCategory() != null
                && Boolean.TRUE.equals(p.getCategory().getIsActive())
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
                && Boolean.TRUE.equals(p.getCategory().getIsActive())
                ? new ProductSummaryResponse.CategoryInfo(
                p.getCategory().getId(), p.getCategory().getName())
                : null;

        return new ProductDetailResponse(
                p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getType(),
                p.getRentalPrice(), p.getRentalPricePerDay(), p.getPurchasePrice(),
                p.getStock(), p.getIsAvailable(),
                fromJson(p.getSizes()), images,
                null, categoryInfo,
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}