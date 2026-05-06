package com.blanchebridal.backend.payment.service.impl;

import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderItem;
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
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.user.entity.User;
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

    private final PaymentRepository  paymentRepository;
    private final OrderRepository    orderRepository;
    private final ProductRepository  productRepository;
    private final PayHereUtil        payHereUtil;
    private final ReceiptService     receiptService;

    @Value("${payhere.merchant-id}") private String merchantId;
    @Value("${payhere.return-url}")  private String returnUrl;
    @Value("${payhere.cancel-url}")  private String cancelUrl;
    @Value("${payhere.notify-url}")  private String notifyUrl;

    @Override
    @Transactional
    public PaymentInitiateResponse initiatePayment(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Access denied to this order");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Order cannot be paid — current status: " + order.getStatus());
        }

        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseGet(() -> Payment.builder()
                        .order(order)
                        .amount(order.getTotalAmount())
                        .method(PaymentMethod.PAYHERE)
                        .status(PaymentStatus.PENDING)
                        .payhereOrderId(orderId.toString())
                        .build());

        paymentRepository.save(payment);

        User user = order.getUser();
        String amountFormatted = "%.2f".formatted(order.getTotalAmount());
        String hash = payHereUtil.generateHash(merchantId, orderId.toString(), amountFormatted, "LKR");

        String itemsDesc = order.getItems() != null && !order.getItems().isEmpty()
                ? order.getItems().get(0).getProductName()
                  + (order.getItems().size() > 1
                     ? " and " + (order.getItems().size() - 1) + " more"
                     : "")
                : "Blanche Bridal Order";

        return PaymentInitiateResponse.builder()
                .merchantId(merchantId)
                .orderId(order.getId().toString())
                .amount(amountFormatted)
                .currency("LKR")
                .hash(hash)
                .itemsDescription(itemsDesc)
                .customerFirstName(user.getFirstName() != null ? user.getFirstName() : "")
                .customerLastName(user.getLastName()  != null ? user.getLastName()  : "")
                .customerEmail(user.getEmail())
                .customerPhone(user.getPhone()        != null ? user.getPhone()     : "0000000000")
                .customerAddress("N/A")
                .customerCity("Colombo")
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .notifyUrl(notifyUrl)
                .build();
    }

    @Override
    @Transactional
    public void handleWebhook(Map<String, String> params) {
        String merchantIdParam  = params.getOrDefault("merchant_id",     "");
        String orderId          = params.getOrDefault("order_id",         "");
        String payhereAmount    = params.getOrDefault("payhere_amount",   "");
        String payhereCurrency  = params.getOrDefault("payhere_currency", "");
        String statusCode       = params.getOrDefault("status_code",      "");
        String receivedHash     = params.getOrDefault("md5sig",           "");
        String payherePaymentId = params.getOrDefault("payment_id",       "");

        String expectedHash = payHereUtil.generateNotifyHash(
                merchantIdParam, orderId, payhereAmount, payhereCurrency, statusCode);

        if (!expectedHash.equalsIgnoreCase(receivedHash)) {
            log.warn("PayHere hash mismatch for order {}. Expected: {} | Got: {}",
                    orderId, expectedHash, receivedHash);
            return;
        }

        if (!"2".equals(statusCode)) {
            log.info("PayHere non-success status {} for order {}", statusCode, orderId);

            // Mark the payment as FAILED for non-success codes so the order
            // scheduler can later clean up PENDING orders.
            if ("0".equals(statusCode) || "-1".equals(statusCode) || "-2".equals(statusCode) || "-3".equals(statusCode)) {
                paymentRepository.findByPayhereOrderId(orderId).ifPresent(payment -> {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    log.info("Payment marked FAILED for order {} (status_code: {})", orderId, statusCode);
                });
            }
            return;
        }

        paymentRepository.findByPayhereOrderId(orderId).ifPresentOrElse(payment -> {

            // Guard against duplicate webhook delivery
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                log.info("Duplicate webhook received for already-completed order {}. Ignoring.", orderId);
                return;
            }

            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPayherePaymentId(payherePaymentId);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);

            Order order = payment.getOrder();
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);

            log.info("Payment COMPLETED for order {}. PayHere ID: {}", orderId, payherePaymentId);

            // Deduct stock now that payment is confirmed.
            // This is the ONLY place stock is reduced — createOrder() does not touch stock.
            if (order.getItems() != null) {
                for (OrderItem item : order.getItems()) {
                    Product product = item.getProduct();
                    if (product != null) {
                        int newStock = Math.max(0, product.getStock() - item.getQuantity());
                        product.setStock(newStock);
                        productRepository.save(product);
                        log.info("[Stock] Reduced stock for product {} ('{}') by {}. New stock: {}",
                                product.getId(), product.getName(), item.getQuantity(), newStock);
                    }
                }
            }

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