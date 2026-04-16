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
    String getReceiptPdfUrl(UUID receiptId, UUID requestingUserId, String role);
}