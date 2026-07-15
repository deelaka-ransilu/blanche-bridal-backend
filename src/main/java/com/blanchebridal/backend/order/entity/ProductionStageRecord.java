package com.blanchebridal.backend.order.entity;

import com.blanchebridal.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "production_stage_records")
@Data @Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionStageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false)
    private ProductionStage currentStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_stage")
    private ProductionStage pendingStage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User proposedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProductionStatus status = ProductionStatus.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_employee")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User assignedEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User reviewedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}