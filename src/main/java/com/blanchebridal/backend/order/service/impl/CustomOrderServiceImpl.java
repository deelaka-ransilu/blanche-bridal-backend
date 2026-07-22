package com.blanchebridal.backend.order.service.impl;

import com.blanchebridal.backend.appointment.entity.CustomDesignRequest;
import com.blanchebridal.backend.appointment.repository.CustomDesignRequestRepository;
import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.dto.req.ConfirmSecondPaymentRequest;
import com.blanchebridal.backend.order.dto.res.OrderItemResponse;
import com.blanchebridal.backend.order.dto.res.OrderResponse;
import com.blanchebridal.backend.order.entity.CustomQuote;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderItem;
import com.blanchebridal.backend.order.entity.OrderMode;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.entity.ProductionStage;
import com.blanchebridal.backend.order.entity.ProductionStageRecord;
import com.blanchebridal.backend.order.entity.SplitType;
import com.blanchebridal.backend.order.repository.CustomQuoteRepository;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.order.repository.ProductionStageRecordRepository;
import com.blanchebridal.backend.order.service.CustomOrderService;
import com.blanchebridal.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOrderServiceImpl implements CustomOrderService {

    private static final BigDecimal INSTALLMENT_RATE = new BigDecimal("0.50");

    private final CustomDesignRequestRepository customDesignRequestRepository;
    private final CustomQuoteRepository customQuoteRepository;
    private final OrderRepository orderRepository;
    private final ProductionStageRecordRepository productionStageRecordRepository;

    @Override
    @Transactional
    public OrderResponse confirmSecondPayment(UUID customDesignRequestId, ConfirmSecondPaymentRequest req,
                                              UUID adminId, String role) {

        boolean isStaff = role != null && role.contains("ADMIN");
        if (!isStaff) {
            throw new UnauthorizedException("Only staff can confirm a custom order's second payment");
        }

        CustomDesignRequest designRequest = customDesignRequestRepository.findById(customDesignRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Custom design request not found: " + customDesignRequestId));

        if (designRequest.getFirstPaymentOrder() == null) {
            throw new ConflictException(
                    "This design request has no first-payment order yet — approve a quote first");
        }

        if (designRequest.getSecondPaymentOrder() != null) {
            throw new ConflictException("Second payment has already been created for this design request");
        }

        CustomQuote latestQuote = customQuoteRepository.findLatestOrNull(customDesignRequestId);
        if (latestQuote == null) {
            throw new ResourceNotFoundException(
                    "No quote found for design request: " + customDesignRequestId);
        }

        if (latestQuote.getSplitType() == SplitType.FULL_UPFRONT) {
            throw new IllegalStateException(
                    "This design request was quoted FULL_UPFRONT — there is no second payment to collect");
        }

        UUID firstPaymentOrderId = designRequest.getFirstPaymentOrder().getId();
        ProductionStageRecord productionRecord = productionStageRecordRepository
                .findByOrderId(firstPaymentOrderId)
                .orElseThrow(() -> new ConflictException(
                        "No production record found for order " + firstPaymentOrderId
                                + " — production tracking must be started before pickup payment can be confirmed"));

        if (productionRecord.getCurrentStage() != ProductionStage.READY_FOR_PICKUP) {
            throw new ConflictException(
                    "Production must reach READY_FOR_PICKUP before the second payment can be confirmed — current stage: "
                            + productionRecord.getCurrentStage());
        }

        BigDecimal remainingAmount = latestQuote.getTotalAmount()
                .multiply(INSTALLMENT_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        User customer = designRequest.getAppointment().getUser();

        Order secondPaymentOrder = Order.builder()
                .user(customer)
                .status(OrderStatus.PENDING)
                .totalAmount(remainingAmount)
                .notes("Custom order final payment (remaining 50%) — auto-generated")
                .orderMode(OrderMode.WALK_IN)
                .paymentMethod(req.getPaymentMethod())
                .isCustomOrder(true)
                .build();

        Order savedOrder = orderRepository.save(secondPaymentOrder);

        designRequest.setSecondPaymentOrder(savedOrder);
        customDesignRequestRepository.save(designRequest);

        log.info("[CustomOrder] Second payment order {} created for design request {} — amount LKR {}, "
                        + "method {}, confirmed by {}",
                savedOrder.getId(), customDesignRequestId, remainingAmount, req.getPaymentMethod(), adminId);

        return toResponse(savedOrder);
    }

    // ─── Mapper (mirrors RentalServiceImpl's private toResponse/toItemResponse) ──

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems() == null
                ? List.of()
                : order.getItems().stream().map(this::toItemResponse).toList();

        String email = order.getUser() != null ? order.getUser().getEmail() : null;
        String firstName = order.getUser() != null ? order.getUser().getFirstName() : null;
        String lastName = order.getUser() != null ? order.getUser().getLastName() : null;

        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .notes(order.getNotes())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .customerEmail(email)
                .customerFirstName(firstName)
                .customerLastName(lastName)
                .fulfillmentMethod(order.getFulfillmentMethod())
                .deliveryAddress(order.getDeliveryAddress())
                .customerPhone(order.getCustomerPhone())
                .orderMode(order.getOrderMode())
                .paymentMethod(order.getPaymentMethod())
                .isRentalDeposit(order.getIsRentalDeposit())
                .discountType(order.getDiscountType())
                .discountValue(order.getDiscountValue())
                .discountReason(order.getDiscountReason())
                .build();
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return OrderItemResponse.builder()
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProductName())
                .productImage(item.getProductImage())
                .productType(item.getProduct() != null ? item.getProduct().getType() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .size(item.getSize())
                .subtotal(subtotal)
                .build();
    }
}