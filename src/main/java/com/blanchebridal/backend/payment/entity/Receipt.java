package com.blanchebridal.backend.payment.entity;

import com.blanchebridal.backend.order.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "receipt_number", unique = true, nullable = false, length = 50)
    private String receiptNumber;

    /**
     * The PDF bytes themselves, stored directly in Postgres. Receipts are
     * generated once and read back rarely (a handful of downloads per
     * order), so there's no real benefit to routing them through an
     * external object store — and doing so was actively broken: Cloudinary
     * blocks raw/PDF delivery on free-tier accounts flagged as "untrusted"
     * (no verified payment method), regardless of delivery type (`upload`
     * or `authenticated` both hit the same account-level restriction; see
     * git history on this file for both attempts). Storing the bytes here
     * sidesteps that entirely.
     */
    // No @Lob here — with Hibernate 6 + PostgreSQL, @Lob on byte[] maps to
    // a Large Object (oid, stored as a bigint reference) rather than inline
    // bytea, causing "column is of type bytea but expression is of type
    // bigint" on insert. A plain byte[] field with just @Column maps
    // directly to bytea, which is what we actually want here (small PDFs,
    // no need for the LO machinery).
    @Column(name = "pdf_data")
    private byte[] pdfData;

    // ─── Legacy Cloudinary fields ───────────────────────────────────────────
    // No longer written for new receipts (see generateReceipt() in
    // ReceiptServiceImpl). Left in place, now nullable, so old rows created
    // before the migration to DB storage aren't destroyed and still carry
    // whatever historical Cloudinary reference they had.
    @Column(name = "storage_key", unique = true, length = 100)
    private String storageKey;

    private Integer storageVersion;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @CreationTimestamp
    @Column(name = "issued_at", updatable = false)
    private LocalDateTime issuedAt;
}