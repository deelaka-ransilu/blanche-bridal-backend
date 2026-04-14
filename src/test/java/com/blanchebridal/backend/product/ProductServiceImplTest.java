package com.blanchebridal.backend.product;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.product.dto.ProductFilters;
import com.blanchebridal.backend.product.dto.req.CreateProductRequest;
import com.blanchebridal.backend.product.dto.req.UpdateProductRequest;
import com.blanchebridal.backend.product.dto.res.ProductDetailResponse;
import com.blanchebridal.backend.product.dto.res.ProductSummaryResponse;
import com.blanchebridal.backend.product.entity.Category;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.entity.ProductImage;
import com.blanchebridal.backend.product.entity.ProductType;
import com.blanchebridal.backend.product.repository.CategoryRepository;
import com.blanchebridal.backend.product.repository.ProductImageRepository;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.product.service.impl.ProductServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    // ObjectMapper is NOT mocked — we use the real one so JSON serialization works
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProductServiceImpl productService;

    private Category category;
    private Product dress;
    private Product accessory;
    private UUID categoryId;
    private UUID dressId;
    private UUID accessoryId;

    @BeforeEach
    void setUp() {
        // Inject the real ObjectMapper manually since @InjectMocks won't pick it up
        // without a Spring context
        productService = new ProductServiceImpl(productRepository, categoryRepository, objectMapper, productImageRepository);

        categoryId   = UUID.randomUUID();
        dressId      = UUID.randomUUID();
        accessoryId  = UUID.randomUUID();

        category = Category.builder()
                .id(categoryId)
                .name("Bridal Gowns")
                .slug("bridal-gowns")
                .createdAt(LocalDateTime.now())
                .build();

        dress = Product.builder()
                .id(dressId)
                .name("Lace Wedding Dress")
                .slug("lace-wedding-dress")
                .description("A beautiful lace dress")
                .type(ProductType.DRESS)
                .category(category)
                .rentalPrice(new BigDecimal("5000.00"))
                .purchasePrice(new BigDecimal("25000.00"))
                .stock(3)
                .sizes("[\"S\",\"M\",\"L\"]")
                .images(new ArrayList<>())
                .isAvailable(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        accessory = Product.builder()
                .id(accessoryId)
                .name("Pearl Tiara")
                .slug("pearl-tiara")
                .description("Elegant pearl tiara")
                .type(ProductType.ACCESSORY)
                .category(category)
                .purchasePrice(new BigDecimal("3500.00"))
                .stock(10)
                .sizes("[]")
                .images(new ArrayList<>())
                .isAvailable(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET PRODUCTS (PAGINATED + FILTERED)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getProducts: returns paginated list of all products")
    void getProducts_returnsAll() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> fakePage = new PageImpl<>(List.of(dress, accessory), pageable, 2);

        // any(Specification.class) — we don't care which spec, just return our fake page
        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class)))
                .thenReturn(fakePage);

        ProductFilters filters = new ProductFilters(null, null, null, null, null, null);
        Page<ProductSummaryResponse> result = productService.getProducts(filters, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().getFirst().name()).isEqualTo("Lace Wedding Dress");
        assertThat(result.getContent().get(1).name()).isEqualTo("Pearl Tiara");
    }

    @Test
    @DisplayName("getProducts: filtered by type DRESS returns only dresses")
    void getProducts_filterByType_returnsDressesOnly() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> fakePage = new PageImpl<>(List.of(dress), pageable, 1);

        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class)))
                .thenReturn(fakePage);

        ProductFilters filters = new ProductFilters(ProductType.DRESS, null, null, null, null, null);
        Page<ProductSummaryResponse> result = productService.getProducts(filters, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().type()).isEqualTo(ProductType.DRESS);
    }

    @Test
    @DisplayName("getProducts: empty result returns empty page")
    void getProducts_noMatch_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class)))
                .thenReturn(emptyPage);

        ProductFilters filters = new ProductFilters(null, null, "nonexistent", null, null, null);
        Page<ProductSummaryResponse> result = productService.getProducts(filters, pageable);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET PRODUCT BY ID
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getProductById: success — returns full product detail")
    void getProductById_success() {
        when(productRepository.findById(dressId)).thenReturn(Optional.of(dress));

        ProductDetailResponse result = productService.getProductById(dressId);

        assertThat(result.id()).isEqualTo(dressId);
        assertThat(result.name()).isEqualTo("Lace Wedding Dress");
        assertThat(result.type()).isEqualTo(ProductType.DRESS);
        assertThat(result.sizes()).containsExactly("S", "M", "L");
        assertThat(result.category().name()).isEqualTo("Bridal Gowns");
    }

    @Test
    @DisplayName("getProductById: fail — unknown id throws ResourceNotFoundException")
    void getProductById_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(productRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET PRODUCT BY SLUG
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getProductBySlug: success — returns product matching slug")
    void getProductBySlug_success() {
        when(productRepository.findBySlug("lace-wedding-dress"))
                .thenReturn(Optional.of(dress));

        ProductDetailResponse result = productService.getProductBySlug("lace-wedding-dress");

        assertThat(result.slug()).isEqualTo("lace-wedding-dress");
        assertThat(result.name()).isEqualTo("Lace Wedding Dress");
    }

    @Test
    @DisplayName("getProductBySlug: fail — unknown slug throws ResourceNotFoundException")
    void getProductBySlug_notFound_throwsException() {
        when(productRepository.findBySlug("unknown-slug")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductBySlug("unknown-slug"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CREATE PRODUCT
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createProduct: success — creates product with images and category")
    void createProduct_success() {
        when(productRepository.existsBySlug("lace-wedding-dress")).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(dress);

        CreateProductRequest request = new CreateProductRequest(
                "Lace Wedding Dress",
                "A beautiful lace dress",
                ProductType.DRESS,
                categoryId,
                new BigDecimal("5000.00"),
                new BigDecimal("25000.00"),
                3,
                List.of("S", "M", "L"),
                List.of("https://cloudinary.com/img1.jpg")
        );

        ProductDetailResponse result = productService.createProduct(request);

        assertThat(result.name()).isEqualTo("Lace Wedding Dress");
        assertThat(result.type()).isEqualTo(ProductType.DRESS);
        assertThat(result.category().id()).isEqualTo(categoryId);
    }

    @Test
    @DisplayName("createProduct: success — no category creates product without category")
    void createProduct_noCategory_success() {
        when(productRepository.existsBySlug("pearl-tiara")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(
                Product.builder()
                        .id(accessoryId).name("Pearl Tiara").slug("pearl-tiara")
                        .type(ProductType.ACCESSORY).category(null)
                        .stock(10).sizes("[]").images(new ArrayList<>())
                        .isAvailable(true).createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now()).build()
        );

        CreateProductRequest request = new CreateProductRequest(
                "Pearl Tiara", "Elegant pearl tiara", ProductType.ACCESSORY,
                null, null, new BigDecimal("3500.00"), 10, List.of(), List.of()
        );

        ProductDetailResponse result = productService.createProduct(request);

        assertThat(result.name()).isEqualTo("Pearl Tiara");
        assertThat(result.category()).isNull();
        // categoryRepository.findById should NEVER be called when categoryId is null
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    @DisplayName("createProduct: fail — duplicate slug throws ConflictException")
    void createProduct_duplicateSlug_throwsConflict() {
        // slugify("Lace Wedding Dress") → "lace-wedding-dress"
        when(productRepository.existsBySlug("lace-wedding-dress")).thenReturn(true);

        CreateProductRequest request = new CreateProductRequest(
                "Lace Wedding Dress", null, ProductType.DRESS,
                null, null, null, 1, List.of(), List.of()
        );

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("createProduct: fail — invalid categoryId throws ResourceNotFoundException")
    void createProduct_invalidCategory_throwsException() {
        UUID unknownCategoryId = UUID.randomUUID();
        when(productRepository.existsBySlug(any())).thenReturn(false);
        when(categoryRepository.findById(unknownCategoryId)).thenReturn(Optional.empty());

        CreateProductRequest request = new CreateProductRequest(
                "New Dress", null, ProductType.DRESS,
                unknownCategoryId, null, null, 1, List.of(), List.of()
        );

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UPDATE PRODUCT
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateProduct: success — updates name, price, and stock")
    void updateProduct_success() {
        when(productRepository.findById(dressId)).thenReturn(Optional.of(dress));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Lace Dress", null, null, null,
                new BigDecimal("4500.00"), null, 5,
                null, null, null
        );

        ProductDetailResponse result = productService.updateProduct(dressId, request);

        assertThat(result.name()).isEqualTo("Updated Lace Dress");
        assertThat(result.rentalPrice()).isEqualByComparingTo("4500.00");
        assertThat(result.stock()).isEqualTo(5);
    }

    @Test
    @DisplayName("updateProduct: success — images replaced when new imageUrls provided")
    void updateProduct_replacesImages() {
        // Add an existing image to the dress
        ProductImage existingImage = ProductImage.builder()
                .id(UUID.randomUUID()).url("https://old.jpg").displayOrder(0).build();
        dress.getImages().add(existingImage);

        when(productRepository.findById(dressId)).thenReturn(Optional.of(dress));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductRequest request = new UpdateProductRequest(
                null, null, null, null, null, null, null,
                null, List.of("https://new1.jpg", "https://new2.jpg"), null
        );

        ProductDetailResponse result = productService.updateProduct(dressId, request);

        // Old image cleared, 2 new images attached
        assertThat(result.images()).hasSize(2);
        assertThat(result.images().get(0).url()).isEqualTo("https://new1.jpg");
        assertThat(result.images().get(1).url()).isEqualTo("https://new2.jpg");
    }

    @Test
    @DisplayName("updateProduct: fail — unknown id throws ResourceNotFoundException")
    void updateProduct_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(productRepository.findById(unknownId)).thenReturn(Optional.empty());

        UpdateProductRequest request = new UpdateProductRequest(
                "X", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> productService.updateProduct(unknownId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DELETE PRODUCT
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteProduct: success — calls deleteById on repository")
    void deleteProduct_success() {
        when(productRepository.findById(dressId)).thenReturn(Optional.of(dress));

        productService.deleteProduct(dressId);

        verify(productRepository, times(1)).deleteById(dressId);
    }

    @Test
    @DisplayName("deleteProduct: fail — unknown id throws ResourceNotFoundException")
    void deleteProduct_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(productRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).deleteById(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UPDATE STOCK
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateStock: success — stock is updated to new value")
    void updateStock_success() {
        when(productRepository.findById(dressId)).thenReturn(Optional.of(dress));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDetailResponse result = productService.updateStock(dressId, 10);

        assertThat(result.stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("updateStock: success — stock can be set to zero (out of stock)")
    void updateStock_setToZero_success() {
        when(productRepository.findById(dressId)).thenReturn(Optional.of(dress));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDetailResponse result = productService.updateStock(dressId, 0);

        assertThat(result.stock()).isZero();
    }

    @Test
    @DisplayName("updateStock: fail — unknown id throws ResourceNotFoundException")
    void updateStock_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(productRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateStock(unknownId, 5))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ═════════════════════════════════════════════════════════════════════════
// DELETE PRODUCT IMAGE
// ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteProductImage: success — removes image that belongs to the product")
    void deleteProductImage_success() {
        UUID imageId = UUID.randomUUID();
        ProductImage image = ProductImage.builder()
                .id(imageId)
                .product(dress)
                .url("https://cloudinary.com/img1.jpg")
                .displayOrder(0)
                .build();

        when(productRepository.findById(dressId)).thenReturn(Optional.of(dress));
        when(productImageRepository.findById(imageId)).thenReturn(Optional.of(image));

        productService.deleteProductImage(dressId, imageId);

        verify(productImageRepository, times(1)).delete(image);
    }

    @Test
    @DisplayName("deleteProductImage: fail — unknown productId throws ResourceNotFoundException")
    void deleteProductImage_productNotFound_throwsException() {
        UUID unknownProductId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(productRepository.findById(unknownProductId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProductImage(unknownProductId, imageId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");

        verify(productImageRepository, never()).findById(any());
        verify(productImageRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteProductImage: fail — unknown imageId throws ResourceNotFoundException")
    void deleteProductImage_imageNotFound_throwsException() {
        UUID unknownImageId = UUID.randomUUID();
        when(productRepository.findById(dressId)).thenReturn(Optional.of(dress));
        when(productImageRepository.findById(unknownImageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProductImage(dressId, unknownImageId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Image not found");

        verify(productImageRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteProductImage: fail — image belongs to a different product throws ResourceNotFoundException")
    void deleteProductImage_imageBelongsToDifferentProduct_throwsException() {
        UUID imageId = UUID.randomUUID();

        // This image is attached to `accessory`, not `dress`
        ProductImage image = ProductImage.builder()
                .id(imageId)
                .product(accessory)
                .url("https://cloudinary.com/other.jpg")
                .displayOrder(0)
                .build();

        when(productRepository.findById(dressId)).thenReturn(Optional.of(dress));
        when(productImageRepository.findById(imageId)).thenReturn(Optional.of(image));

        assertThatThrownBy(() -> productService.deleteProductImage(dressId, imageId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Image not found on this product");

        verify(productImageRepository, never()).delete(any());
    }
}