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

    // FIRST payment: 50% of rental fee, collected at fitting-booking time.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    // SECOND payment: remaining 50% rental fee + security deposit, collected
    // at handover (pickup).
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handover_order_id", unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order handoverOrder;

    @Column(name = "rental_start", nullable = false)
    private LocalDate rentalStart;

    @Column(name = "rental_end", nullable = false)
    private LocalDate rentalEnd;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RentalStatus status;

    // Total rental fee (both halves combined). Replaces the old dual-purpose
    // depositAmount field, which is kept only for backward compatibility with
    // rows written by the previous flow.
    @Column(name = "rental_fee", precision = 10, scale = 2)
    private BigDecimal rentalFee;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    // Refundable security deposit — 30% of rentalFee, collected alongside the
    // second (handover) payment. Buffer for damage cost + late fees.
    @Column(name = "security_deposit_amount", precision = 10, scale = 2)
    private BigDecimal securityDepositAmount;

    @Column(name = "security_deposit_refunded_amount", precision = 10, scale = 2)
    private BigDecimal securityDepositRefundedAmount;

    @Column(name = "damage_cost", precision = 10, scale = 2)
    private BigDecimal damageCost;

    @Column(name = "late_fee_amount", precision = 10, scale = 2)
    private BigDecimal lateFeeAmount;

    // If damageCost + lateFeeAmount exceeds securityDepositAmount, the
    // difference the customer still owes on top of the forfeited deposit.
    @Column(name = "amount_owed_by_customer", precision = 10, scale = 2)
    private BigDecimal amountOwedByCustomer;

    @Column(name = "handover_confirmed_at")
    private LocalDateTime handoverConfirmedAt;

    @Column(name = "balance_due", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal balanceDue = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Fitting appointment (type RENTAL_FITTING), booked on/before
    // rentalStart - 2 days.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Appointment appointment;
}