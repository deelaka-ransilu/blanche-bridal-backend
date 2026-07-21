package com.blanchebridal.backend.appointment.entity;

import com.blanchebridal.backend.order.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// Holds the fields specific to a CUSTOM_CONSULTATION appointment. Kept as
// its own table (1:1 with Appointment) rather than columns on Appointment
// itself, same reasoning as Order -> Payment -> Receipt: these fields are
// meaningless for FITTING/RENTAL_PICKUP/PURCHASE appointments, so they don't
// belong on the shared Appointment row.
@Entity
@Table(name = "custom_design_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Appointment appointment;

    // add these two fields to CustomDesignRequest.java, alongside the existing
// appointment/occasionType/etc fields

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "first_payment_order_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order firstPaymentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "second_payment_order_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order secondPaymentOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "occasion_type", nullable = false, length = 20)
    private OccasionType occasionType;

    @Column(name = "occasion_date", nullable = false)
    private LocalDate occasionDate;

    @Column(name = "style_preferences", columnDefinition = "TEXT")
    private String stylePreferences;

    // Stored as JSON array string, same convention as Product.sizes:
    // ["https://res.cloudinary.com/.../img1.jpg", "..."]
    @Column(name = "reference_images", columnDefinition = "TEXT")
    private String referenceImages;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}