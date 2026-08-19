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
    @JoinColumn(name = "order_id")
    private Order order;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_id")
    private com.blanchebridal.backend.rental.entity.Rental rental;

    @Column(name = "receipt_number", unique = true, nullable = false, length = 50)
    private String receiptNumber;

    @Column(name = "pdf_data")
    private byte[] pdfData;

    @Column(name = "storage_key", unique = true, length = 100)
    private String storageKey;

    private Integer storageVersion;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_type", nullable = false, length = 20)
    @Builder.Default
    private ReceiptType type = ReceiptType.PAYMENT;

    @CreationTimestamp
    @Column(name = "issued_at", updatable = false)
    private LocalDateTime issuedAt;
}