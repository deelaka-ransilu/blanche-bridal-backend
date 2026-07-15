package com.blanchebridal.backend.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
@Data @Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Category category;

    // Flat one-time rental fee. Used as a fallback when
    // rentalPricePerDay is not set (see RentalServiceImpl.bookRental).
    @Column(name = "rental_price", precision = 10, scale = 2)
    private BigDecimal rentalPrice;

    // Optional per-day rate. When set, the rental fee at booking time is
    // this rate multiplied by the number of days between rentalStart and
    // rentalEnd, instead of the flat rentalPrice.
    @Column(name = "rental_price_per_day", precision = 10, scale = 2)
    private BigDecimal rentalPricePerDay;

    @Column(name = "purchase_price", precision = 10, scale = 2)
    private BigDecimal purchasePrice;

    @Column(nullable = false)
    private Integer stock = 0;

    @Column(columnDefinition = "TEXT")
    private String sizes; // stored as JSON array string: ["XS","S","M"]

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ProductImage> images;

    @Column(name = "is_available")
    private Boolean isAvailable = true;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}