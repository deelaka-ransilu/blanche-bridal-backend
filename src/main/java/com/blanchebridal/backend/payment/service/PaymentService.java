package com.blanchebridal.backend.payment.service;

import com.blanchebridal.backend.payment.dto.res.PaymentInitiateResponse;
import com.blanchebridal.backend.payment.dto.res.PaymentStatusResponse;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {
    PaymentInitiateResponse initiatePayment(UUID orderId, UUID userId);
    void handleWebhook(Map<String, String> params);
    PaymentStatusResponse getPaymentStatus(UUID orderId, UUID userId);
}