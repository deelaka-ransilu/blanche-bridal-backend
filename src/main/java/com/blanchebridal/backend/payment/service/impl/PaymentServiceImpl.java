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
import com.blanchebridal.backend.payment.entity.Receipt;
import com.blanchebridal.backend.payment.repository.PaymentRepository;
import com.blanchebridal.backend.payment.service.PaymentService;
import com.blanchebridal.backend.payment.service.ReceiptService;
import com.blanchebridal.backend.payment.util.PayHereUtil;
import com.blanchebridal.backend.rental.entity.Rental;
import com.blanchebridal.backend.rental.entity.RentalStatus;
import com.blanchebridal.backend.rental.repository.RentalRepository;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository  paymentRepository;
    private final OrderRepository orderRepository;
    private final RentalRepository   rentalRepository;
    private final PayHereUtil        payHereUtil;
    private final ReceiptService     receiptService;
    private final EmailService       emailService;

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

        if (order.getPaymentMethod() == PaymentMethod.CASH) {
            throw new IllegalStateException(
                    "This order uses cash payment — use the confirm-cash endpoint instead");
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

        String returnUrlWithOrder = returnUrl + "?orderId=" + order.getId();
        String cancelUrlWithOrder = cancelUrl + "?orderId=" + order.getId();

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
                .returnUrl(returnUrlWithOrder)
                .cancelUrl(cancelUrlWithOrder)
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

            Receipt receipt = receiptService.generateReceipt(order, payment);
            sendOrderConfirmationEmailSafely(order, receipt.getPdfData());

            if (Boolean.TRUE.equals(order.getIsRentalDeposit())) {
                handleRentalPaymentConfirmed(order);
            }

        }, () -> log.warn("Webhook received for unknown payhereOrderId: {}", orderId));
    }

    @Override
    @Transactional
    public PaymentStatusResponse confirmCashPayment(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getPaymentMethod() != PaymentMethod.CASH) {
            throw new IllegalStateException("Order is not set up for cash payment");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Order cannot be confirmed — current status: " + order.getStatus());
        }

        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseGet(() -> Payment.builder()
                        .order(order)
                        .amount(order.getTotalAmount())
                        .method(PaymentMethod.CASH)
                        .status(PaymentStatus.PENDING)
                        .build());

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("[Payment] Cash payment already confirmed for order {}. Ignoring.", orderId);
            return PaymentStatusResponse.builder().status(PaymentStatus.COMPLETED.name()).build();
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        log.info("[Payment] Cash payment CONFIRMED for order {}", orderId);

        Receipt receipt = receiptService.generateReceipt(order, payment);
        sendOrderConfirmationEmailSafely(order, receipt.getPdfData());

        if (Boolean.TRUE.equals(order.getIsRentalDeposit())) {
            handleRentalPaymentConfirmed(order);
        }

        return PaymentStatusResponse.builder().status(PaymentStatus.COMPLETED.name()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(UUID orderId, UUID userId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        boolean isCustomer = role != null &&
                (role.equals("ROLE_CUSTOMER") || role.equals("CUSTOMER"));

        if (isCustomer && (order.getUser() == null || !order.getUser().getId().equals(userId))) {
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

    // ─── Rental-payment hook ────────────────────────────────────────────────

    /**
     * Called from handleWebhook() and confirmCashPayment() when the confirmed
     * order is a synthetic rental order (Order.isRentalDeposit = true).
     * Dispatches to the fitting-payment or handover-payment handler depending
     * on which of the rental's two order slots this order fills — a rental
     * now has TWO synthetic orders (fitting 50% + handover 50%+deposit), so
     * this can no longer assume it's always the first one.
     */
    private void handleRentalPaymentConfirmed(Order order) {
        rentalRepository.findByOrder_Id(order.getId()).ifPresentOrElse(
                this::handleFittingPaymentConfirmed,
                () -> rentalRepository.findByHandoverOrder_Id(order.getId()).ifPresentOrElse(
                        this::handleHandoverPaymentConfirmed,
                        () -> log.warn("[Rental] Order {} flagged isRentalDeposit=true but matches "
                                + "neither a rental's fitting order nor handover order.", order.getId())
                )
        );
    }

    /**
     * First payment (50% rental fee, paid at fitting-booking time) confirmed.
     * Flips PENDING_PAYMENT -> BOOKED. Does not touch Product.stock — rentals
     * don't deduct stock.
     */
    private void handleFittingPaymentConfirmed(Rental rental) {
        if (rental.getStatus() != RentalStatus.PENDING_PAYMENT) {
            log.info("[Rental] Fitting payment confirmed for rental {} but it's already in status {} — skipping.",
                    rental.getId(), rental.getStatus());
            return;
        }
        rental.setStatus(RentalStatus.BOOKED);
        rentalRepository.save(rental);
        log.info("[Rental] Rental {} transitioned PENDING_PAYMENT -> BOOKED (fitting payment confirmed).",
                rental.getId());
    }

    /**
     * Second payment (remaining 50% + security deposit, paid at handover)
     * confirmed. Marks handoverConfirmedAt and activates the rental
     * immediately — handover only happens at/after rentalStart, so there's no
     * need to wait for the scheduler's date-based activation.
     */
    private void handleHandoverPaymentConfirmed(Rental rental) {
        if (rental.getStatus() != RentalStatus.BOOKED) {
            log.info("[Rental] Handover payment confirmed for rental {} but it's in status {}, not BOOKED — skipping.",
                    rental.getId(), rental.getStatus());
            return;
        }
        rental.setHandoverConfirmedAt(LocalDateTime.now());
        rental.setStatus(RentalStatus.ACTIVE);
        rentalRepository.save(rental);
        log.info("[Rental] Rental {} transitioned BOOKED -> ACTIVE (handover payment confirmed, dress handed over).",
                rental.getId());
    }

    // ─── Order-confirmation email hook ─────────────────────────────────────

    /**
     * receiptPdfBytes is the PDF for THIS payment's own receipt (fitting or
     * handover order each generate their own via generateReceipt()), attached
     * to this confirmation email only — never a combined or cross-payment PDF.
     */
    private void sendOrderConfirmationEmailSafely(Order order, byte[] receiptPdfBytes) {
        try {
            User user = order.getUser();
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                log.warn("[Payment] Order {} has no user/email — skipping confirmation email.", order.getId());
                return;
            }

            List<String> itemSummaries = order.getItems() != null
                    ? order.getItems().stream()
                      .map(item -> item.getProductName())
                      .toList()
                    : List.of();

            String firstName = user.getFirstName() != null ? user.getFirstName() : "";
            String lastName  = user.getLastName()  != null ? user.getLastName()  : "";
            String customerName = (firstName + " " + lastName).trim();
            if (customerName.isEmpty()) {
                customerName = "Customer";
            }

            emailService.sendOrderConfirmationEmail(
                    user.getEmail(),
                    customerName,
                    order.getId().toString(),
                    order.getTotalAmount(),
                    itemSummaries,
                    receiptPdfBytes
            );

            log.info("[Payment] Order confirmation email sent for order {} to {} (receipt attached: {})",
                    order.getId(), user.getEmail(), receiptPdfBytes != null);

        } catch (Exception e) {
            log.error("[Payment] Failed to send order confirmation email for order {}: {}",
                    order.getId(), e.getMessage(), e);
        }
    }
}