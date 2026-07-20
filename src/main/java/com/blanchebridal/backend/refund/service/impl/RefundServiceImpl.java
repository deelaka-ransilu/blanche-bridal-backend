package com.blanchebridal.backend.refund.service.impl;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.payment.entity.Payment;
import com.blanchebridal.backend.payment.entity.PaymentStatus;
import com.blanchebridal.backend.payment.repository.PaymentRepository;
import com.blanchebridal.backend.refund.dto.BankDetailsResponse;
import com.blanchebridal.backend.refund.dto.RefundResponse;
import com.blanchebridal.backend.refund.dto.SubmitBankDetailsRequest;
import com.blanchebridal.backend.refund.entity.Refund;
import com.blanchebridal.backend.refund.entity.RefundBankDetails;
import com.blanchebridal.backend.refund.repository.RefundBankDetailsRepository;
import com.blanchebridal.backend.refund.repository.RefundRepository;
import com.blanchebridal.backend.refund.service.RefundService;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundBankDetailsRepository refundBankDetailsRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public RefundResponse createRefund(UUID orderId, String reason, String proofImageUrl, UUID adminId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order: " + orderId));

        // Check "already refunded" BEFORE "is payment COMPLETED" -- a successful
        // refund flips Payment.status to REFUNDED, so on a repeat attempt the old
        // check order (COMPLETED-check first) always caught it as "not COMPLETED"
        // and threw a 400 IllegalStateException, never reaching this 409 branch.
        if (refundRepository.existsByOrder_Id(orderId)) {
            throw new ConflictException("Order has already been refunded: " + orderId);
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Cannot refund a payment that is not COMPLETED (current status: "
                    + payment.getStatus() + ")");
        }

        if (proofImageUrl == null || proofImageUrl.isBlank()) {
            throw new IllegalArgumentException("proofImageUrl is required to process a refund");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + adminId));

        Refund refund = Refund.builder()
                .order(order)
                .amount(payment.getAmount())
                .reason(reason)
                .proofImageUrl(proofImageUrl)
                .processedByAdmin(admin)
                .build();

        refund = refundRepository.save(refund);

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        log.info("[Refund] Refund {} created for order {} by admin {}", refund.getId(), orderId, adminId);

        sendRefundEmailSafely(order, refund);

        return RefundResponse.builder()
                .id(refund.getId())
                .orderId(order.getId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .proofImageUrl(refund.getProofImageUrl())
                .processedByAdminId(admin.getId())
                .createdAt(refund.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public BankDetailsResponse submitBankDetails(UUID orderId, UUID customerId, SubmitBankDetailsRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getUser() == null || !order.getUser().getId().equals(customerId)) {
            throw new UnauthorizedException("Access denied to this order");
        }

        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw new IllegalStateException("Bank details can only be submitted for a cancelled order");
        }

        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order: " + orderId));

        // Once the refund is REFUNDED there's nothing left to pay out to —
        // bank details after that point would have no effect, so block it
        // rather than silently accept details nobody will use.
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("Order has already been refunded — bank details can no longer be changed");
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("No refund is owed for this order");
        }

        // Upsert — customer may correct previously submitted details before
        // the refund is actually processed by an admin.
        RefundBankDetails details = refundBankDetailsRepository.findByOrder_Id(orderId)
                .orElseGet(() -> RefundBankDetails.builder().order(order).build());

        details.setAccountHolderName(request.getAccountHolderName());
        details.setAccountNumber(request.getAccountNumber());
        details.setBankName(request.getBankName());
        details.setBranch(request.getBranch());

        details = refundBankDetailsRepository.save(details);

        log.info("[Refund] Bank details submitted for order {} by customer {}", orderId, customerId);

        return BankDetailsResponse.builder()
                .accountHolderName(details.getAccountHolderName())
                .accountNumber(details.getAccountNumber())
                .bankName(details.getBankName())
                .branch(details.getBranch())
                .submittedAt(details.getSubmittedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BankDetailsResponse getBankDetails(UUID orderId) {
        RefundBankDetails details = refundBankDetailsRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No bank details submitted for order: " + orderId));

        return BankDetailsResponse.builder()
                .accountHolderName(details.getAccountHolderName())
                .accountNumber(details.getAccountNumber())
                .bankName(details.getBankName())
                .branch(details.getBranch())
                .submittedAt(details.getSubmittedAt())
                .build();
    }

    // Wrapped in try/catch so an email failure never rolls back or interrupts
    // the refund transaction it's called from — same pattern as
    // OrderServiceImpl's send*EmailSafely methods.
    private void sendRefundEmailSafely(Order order, Refund refund) {
        try {
            User customer = order.getUser();
            if (customer == null || customer.getEmail() == null) {
                log.warn("[Refund] Skipped refund email for order {} — customer or email missing (customer null: {})",
                        order.getId(), customer == null);
                return;
            }

            emailService.sendRefundProcessedEmail(
                    customer.getEmail(),
                    customer.getFirstName() + " " + customer.getLastName(),
                    order.getId().toString().substring(0, 8).toUpperCase(),
                    refund.getAmount(),
                    refund.getReason(),
                    refund.getProofImageUrl()
            );
            log.info("[Refund] Refund email sent for order {} to {}", order.getId(), customer.getEmail());
        } catch (Exception e) {
            log.warn("Failed to send refund-processed email for order {}: {}",
                    order.getId(), e.getMessage());
        }
    }
}