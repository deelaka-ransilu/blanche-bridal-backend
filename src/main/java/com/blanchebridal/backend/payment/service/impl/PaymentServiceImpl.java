package com.blanchebridal.backend.payment.service.impl;

import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.payment.dto.res.PaymentInitiateResponse;
import com.blanchebridal.backend.payment.dto.res.PaymentStatusResponse;
import com.blanchebridal.backend.payment.entity.Payment;
import com.blanchebridal.backend.payment.entity.PaymentMethod;
import com.blanchebridal.backend.payment.entity.PaymentStatus;
import com.blanchebridal.backend.payment.repository.PaymentRepository;
import com.blanchebridal.backend.payment.service.PaymentService;
import com.blanchebridal.backend.payment.service.ReceiptService;
import com.blanchebridal.backend.payment.util.PayHereUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PayHereUtil payHereUtil;
    private final ReceiptService receiptService;

    @Value("${payhere.merchant-id}")
    private String merchantId;

    @Value("${payhere.return-url}")
    private String returnUrl;

    @Value("${payhere.cancel-url}")
    private String cancelUrl;

    @Value("${payhere.notify-url}")
    private String notifyUrl;

    @Override
    @Transactional
    public PaymentInitiateResponse initiatePayment(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Verify the order belongs to the requesting customer
        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Access denied to this order");
        }

        // Only PENDING orders can be paid
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Order cannot be paid — current status: " + order.getStatus());
        }

        // Create or reuse a PENDING payment record
        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseGet(() -> Payment.builder()
                        .order(order)
                        .amount(order.getTotalAmount())
                        .method(PaymentMethod.PAYHERE)
                        .status(PaymentStatus.PENDING)
                        .payhereOrderId(orderId.toString())
                        .build());

        // Always save to ensure payhereOrderId is persisted
        paymentRepository.save(payment);

        String amountFormatted = "%.2f".formatted(order.getTotalAmount());
        String hash = payHereUtil.generateHash(merchantId, orderId.toString(),
                amountFormatted, "LKR");

        String firstName = order.getUser().getFirstName() != null
                ? order.getUser().getFirstName() : "";
        String lastName = order.getUser().getLastName() != null
                ? order.getUser().getLastName() : "";

        // Build a short items description from the first item snapshot
        String itemsDesc = order.getItems() != null && !order.getItems().isEmpty()
                ? order.getItems().get(0).getProductName()
                  + (order.getItems().size() > 1
                     ? " and " + (order.getItems().size() - 1) + " more"
                     : "")
                : "Blanche Bridal Order";

        return PaymentInitiateResponse.builder()
                .merchantId(merchantId)
                .orderId(orderId.toString())
                .amount(amountFormatted)
                .currency("LKR")
                .hash(hash)
                .itemsDescription(itemsDesc)
                .customerFirstName(firstName)
                .customerLastName(lastName)
                .customerEmail(order.getUser().getEmail())
                .returnUrl(returnUrl)      // ← needs return-url in yml
                .cancelUrl(cancelUrl)      // ← needs cancel-url in yml
                .notifyUrl(notifyUrl)
                .build();
    }

    @Override
    @Transactional
    public void handleWebhook(Map<String, String> params) {
        // Extract all fields PayHere sends
        String merchantIdParam  = params.getOrDefault("merchant_id", "");
        String orderId          = params.getOrDefault("order_id", "");
        String payhereAmount    = params.getOrDefault("payhere_amount", "");
        String payhereCurrency  = params.getOrDefault("payhere_currency", "");
        String statusCode       = params.getOrDefault("status_code", "");
        String receivedHash     = params.getOrDefault("md5sig", "");
        String payherePaymentId = params.getOrDefault("payment_id", "");

        // Regenerate hash on our side and compare
        String expectedHash = payHereUtil.generateHash(
                merchantIdParam, orderId, payhereAmount, payhereCurrency);

        if (!expectedHash.equalsIgnoreCase(receivedHash)) {
            // Log and return — ALWAYS return 200 to PayHere even on mismatch
            log.warn("PayHere webhook hash mismatch for order {}. " +
                    "Expected: {} | Received: {}", orderId, expectedHash, receivedHash);
            return;
        }

        // status_code "2" = successful payment
        if (!"2".equals(statusCode)) {
            log.info("PayHere webhook received non-success status {} for order {}",
                    statusCode, orderId);
            return;
        }

        // Update payment record
        paymentRepository.findByPayhereOrderId(orderId).ifPresentOrElse(payment -> {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPayherePaymentId(payherePaymentId);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Update order to CONFIRMED
            Order order = payment.getOrder();
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);

            log.info("Payment completed for order {}. PayHere payment ID: {}",
                    orderId, payherePaymentId);
             receiptService.generateReceipt(order, payment);

        }, () -> log.warn("Webhook received for unknown payhereOrderId: {}", orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Access denied to this order");
        }

        return paymentRepository.findByOrder_Id(orderId)
                .map(p -> PaymentStatusResponse.builder()
                        .status(p.getStatus().name())
                        .build())
                .orElse(PaymentStatusResponse.builder()
                        .status(PaymentStatus.PENDING.name())
                        .build());
    }
}