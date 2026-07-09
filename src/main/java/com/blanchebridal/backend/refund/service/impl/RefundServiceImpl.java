package com.blanchebridal.backend.refund.service.impl;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.payment.entity.Payment;
import com.blanchebridal.backend.payment.entity.PaymentStatus;
import com.blanchebridal.backend.payment.repository.PaymentRepository;
import com.blanchebridal.backend.refund.dto.RefundResponse;
import com.blanchebridal.backend.refund.entity.Refund;
import com.blanchebridal.backend.refund.repository.RefundRepository;
import com.blanchebridal.backend.refund.service.RefundService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RefundResponse createRefund(UUID orderId, String reason, UUID adminId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order: " + orderId));

        // Check "already refunded" BEFORE "is payment COMPLETED" -- a successful
        // refund flips Payment.status to REFUNDED, so on a repeat attempt the old
        // check order (COMPLETED-check first) always caught it as "not COMPLETED"
        // and threw a 400 IllegalStateException, never reaching this 409 branch.
        // Checking existence first ensures a repeat refund attempt gets the
        // correct, more specific 409 CONFLICT response instead of a misleading 400.
        if (refundRepository.existsByOrder_Id(orderId)) {
            throw new ConflictException("Order has already been refunded: " + orderId);
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Cannot refund a payment that is not COMPLETED (current status: "
                    + payment.getStatus() + ")");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + adminId));

        Refund refund = Refund.builder()
                .order(order)
                .amount(payment.getAmount())
                .reason(reason)
                .processedByAdmin(admin)
                .build();

        refund = refundRepository.save(refund);

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        return RefundResponse.builder()
                .id(refund.getId())
                .orderId(order.getId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .processedByAdminId(admin.getId())
                .createdAt(refund.getCreatedAt())
                .build();
    }
}