package com.blanchebridal.backend.payment.service;

import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.payment.dto.res.ReceiptResponse;
import com.blanchebridal.backend.payment.entity.Payment;
import com.blanchebridal.backend.payment.entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ReceiptService {
    Receipt generateReceipt(Order order, Payment payment);
    List<ReceiptResponse> getMyReceipts(UUID userId);
    Page<ReceiptResponse> getAllReceipts(Pageable pageable);

    // Kept for backwards compatibility with the existing /pdf endpoint.
    // Returns the legacy Cloudinary pdfUrl if present (old receipts only) —
    // will be null for any receipt generated after the DB-storage switch.
    String getReceiptPdfUrl(UUID receiptId, UUID requestingUserId, String role);

    // No longer throws IOException/InterruptedException — there's no
    // outbound network call anymore, just a DB read.
    byte[] downloadReceiptPdf(UUID receiptId, UUID requestingUserId, String role);

    String getReceiptFilename(UUID receiptId, UUID requestingUserId, String role);
    ReceiptResponse getReceiptByOrderId(UUID orderId, UUID requestingUserId, String role);
}