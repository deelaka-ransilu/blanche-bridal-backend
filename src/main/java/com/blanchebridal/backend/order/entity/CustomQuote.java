package com.blanchebridal.backend.order.entity;

import com.blanchebridal.backend.appointment.entity.CustomDesignRequest;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "custom_quotes")
@Data @Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_design_request_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CustomDesignRequest customDesignRequest;

    @Column(nullable = false)
    private int version;

    @Column(name = "fabric_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal fabricAmount;

    @Column(name = "labor_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal laborAmount;

    @Column(name = "embellishment_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal embellishmentAmount;

    @Column(name = "alterations_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal alterationsAmount;

    @Column(name = "other_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal otherAmount;

    @Column(name = "other_note", columnDefinition = "TEXT")
    private String otherNote;

    // Stored, not recomputed — same convention as Rental.rentalFee.
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private QuoteStatus status = QuoteStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}