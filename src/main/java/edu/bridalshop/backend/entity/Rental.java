package edu.bridalshop.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rentals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rental {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rental_id")
    private Integer rentalId;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dress_id", nullable = false)
    private Dress dress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    // NIC stored on rental record — NOT on customer profile
    @Column(name = "nic_number", nullable = false, length = 20)
    private String nicNumber;

    // ── Price snapshots (copied at booking time — never affected by dress price changes) ──

    @Column(name = "rental_price_per_day", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentalPricePerDay;

    @Column(name = "rental_period_days", nullable = false)
    private Integer rentalPeriodDays;

    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "total_rental_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRentalFee;           // rentalPricePerDay x rentalPeriodDays

    @Column(name = "total_paid_upfront", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPaidUpfront;          // totalRentalFee + depositAmount

    // ── Lifecycle timestamps ──────────────────────────────────────────────────

    @Column(name = "handed_over_at")
    private LocalDateTime handedOverAt;           // NULL until dress handed to customer

    @Column(name = "due_date")
    private LocalDate dueDate;                    // handedOverAt + rentalPeriodDays

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;             // NULL until returned

    // ── Return financials (populated on return) ───────────────────────────────

    @Column(name = "days_late", nullable = false)
    private Integer daysLate;

    @Column(name = "late_fine", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFine;

    @Column(name = "total_damage_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDamageCost;           // SUM of damage items

    @Column(name = "total_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDeductions;           // lateFine + totalDamageCost

    @Column(name = "deposit_refunded", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositRefunded;           // MAX(0, deposit - deductions)

    @Column(name = "outstanding_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal outstandingBalance;        // MAX(0, deductions - deposit)

    // ── Status ────────────────────────────────────────────────────────────────

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "return_notes", columnDefinition = "TEXT")
    private String returnNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "rental", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RentalDamageItem> damageItems = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // Default all financial fields to zero
        if (daysLate          == null) daysLate          = 0;
        if (lateFine          == null) lateFine          = BigDecimal.ZERO;
        if (totalDamageCost   == null) totalDamageCost   = BigDecimal.ZERO;
        if (totalDeductions   == null) totalDeductions   = BigDecimal.ZERO;
        if (depositRefunded   == null) depositRefunded   = BigDecimal.ZERO;
        if (outstandingBalance == null) outstandingBalance = BigDecimal.ZERO;
        if (status            == null) status            = "BOOKED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}