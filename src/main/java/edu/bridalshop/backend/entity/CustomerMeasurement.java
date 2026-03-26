package edu.bridalshop.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_measurements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "measurement_id")
    private Integer measurementId;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    // The customer this measurement belongs to (must be CUSTOMER role)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    // The ADMIN who recorded this measurement
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by", nullable = false)
    private User recordedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Body measurements (all cm, all nullable) ──────────────────────────────

    @Column(name = "height_with_shoes", precision = 5, scale = 2)
    private BigDecimal heightWithShoes;

    @Column(name = "hollow_to_hem", precision = 5, scale = 2)
    private BigDecimal hollowToHem;

    @Column(name = "full_bust", precision = 5, scale = 2)
    private BigDecimal fullBust;

    @Column(name = "under_bust", precision = 5, scale = 2)
    private BigDecimal underBust;

    @Column(name = "natural_waist", precision = 5, scale = 2)
    private BigDecimal naturalWaist;

    @Column(name = "full_hip", precision = 5, scale = 2)
    private BigDecimal fullHip;

    @Column(name = "shoulder_width", precision = 5, scale = 2)
    private BigDecimal shoulderWidth;

    @Column(name = "torso_length", precision = 5, scale = 2)
    private BigDecimal torsoLength;

    @Column(name = "thigh_circumference", precision = 5, scale = 2)
    private BigDecimal thighCircumference;

    @Column(name = "waist_to_knee", precision = 5, scale = 2)
    private BigDecimal waistToKnee;

    @Column(name = "waist_to_floor", precision = 5, scale = 2)
    private BigDecimal waistToFloor;

    @Column(name = "armhole", precision = 5, scale = 2)
    private BigDecimal armhole;

    @Column(name = "bicep_circumference", precision = 5, scale = 2)
    private BigDecimal bicepCircumference;

    @Column(name = "elbow_circumference", precision = 5, scale = 2)
    private BigDecimal elbowCircumference;

    @Column(name = "wrist_circumference", precision = 5, scale = 2)
    private BigDecimal wristCircumference;

    @Column(name = "sleeve_length", precision = 5, scale = 2)
    private BigDecimal sleeveLength;

    @Column(name = "upper_bust", precision = 5, scale = 2)
    private BigDecimal upperBust;

    @Column(name = "bust_apex_distance", precision = 5, scale = 2)
    private BigDecimal bustApexDistance;

    @Column(name = "shoulder_to_bust_point", precision = 5, scale = 2)
    private BigDecimal shoulderToBustPoint;

    @Column(name = "neck_circumference", precision = 5, scale = 2)
    private BigDecimal neckCircumference;

    @Column(name = "train_length", precision = 5, scale = 2)
    private BigDecimal trainLength;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}