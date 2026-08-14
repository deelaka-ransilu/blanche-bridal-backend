package com.blanchebridal.backend.rental.entity;

import com.blanchebridal.backend.appointment.entity.Appointment;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rentals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rental {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Product product;

    // ADVANCE: first payment (50% of dressValue), collected at booking time.
    // SAME_DAY: the ONLY payment (100% of dressValue) — handoverOrder is
    // never set for this path.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    // ADVANCE only: second payment (remaining 50% of dressValue), collected
    // at pickup/handover. Always null for SAME_DAY — see bookingPath.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handover_order_id", unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order handoverOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_path", nullable = false, length = 20)
    private RentalBookingPath bookingPath;

    @Column(name = "rental_start", nullable = false)
    private LocalDate rentalStart;

    @Column(name = "rental_end", nullable = false)
    private LocalDate rentalEnd;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RentalStatus status;

    // Snapshotted from Product.dressValue at booking time — the full
    // replacement value of the dress, and the total deposit held by the
    // shop once fully paid (either path). Locked in here so a later change
    // to the product's dressValue never alters an in-progress rental.
    @Column(name = "dress_value", precision = 10, scale = 2)
    private BigDecimal dressValue;

    // Rental fee for the booked date range (flat or per-day × days).
    // Deducted from dressValue at return, same as before.
    @Column(name = "rental_fee", precision = 10, scale = 2)
    private BigDecimal rentalFee;

    @Column(name = "damage_cost", precision = 10, scale = 2)
    private BigDecimal damageCost;

    @Column(name = "late_fee_amount", precision = 10, scale = 2)
    private BigDecimal lateFeeAmount;

    // refund = dressValue - rentalFee - damageCost (- lateFeeAmount).
    // Never negative — see markReturned in Step 5.
    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    // If damageCost + lateFeeAmount exceeds dressValue - rentalFee, the
    // shortfall the customer still owes on top of getting nothing back.
    @Column(name = "amount_owed_by_customer", precision = 10, scale = 2)
    private BigDecimal amountOwedByCustomer;

    @Column(name = "handover_confirmed_at")
    private LocalDateTime handoverConfirmedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Fitting appointment (type RENTAL_FITTING). ADVANCE only — SAME_DAY has
    // no separate fitting visit, the customer is picking up today.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Appointment appointment;
}