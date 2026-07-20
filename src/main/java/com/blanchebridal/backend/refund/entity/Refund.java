package com.blanchebridal.backend.refund.entity;

import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refunds")
@Data @Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_admin_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User processedByAdmin;

    // Screenshot/receipt of the manual bank transfer, uploaded by admin at
    // the same time as issuing the refund. Nullable only for safety at the
    // entity level — the service layer requires it before creating a Refund
    // at all (see RefundServiceImpl.createRefund).
    @Column(name = "proof_image_url", length = 500)
    private String proofImageUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}