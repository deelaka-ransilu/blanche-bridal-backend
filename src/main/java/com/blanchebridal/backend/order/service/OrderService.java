package com.blanchebridal.backend.order.service;

import com.blanchebridal.backend.order.dto.req.CreateOrderRequest;
import com.blanchebridal.backend.order.dto.res.OrderResponse;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.payment.entity.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest req, UUID callerId, String role);
    Page<OrderResponse> getAllOrders(OrderStatus status, Pageable pageable);
    Page<OrderResponse> getMyOrders(UUID userId, Pageable pageable);
    OrderResponse getOrderById(UUID id, UUID requestingUserId, String role);
    OrderResponse updateOrderStatus(UUID id, OrderStatus newStatus);

    /**
     * Cancels the order if (and only if) it is currently PENDING.
     * Returns true if the order was actually cancelled by this call, false
     * if it was a no-op (order was already in a non-PENDING state, e.g.
     * already CONFIRMED, CANCELLED, or COMPLETED). Previously void — callers
     * could not distinguish "actually cancelled" from "silently did nothing"
     * (Backend Issue #3 / STATUS.md).
     */
    boolean cancelOrder(UUID id, UUID userId);

    OrderResponse updatePaymentMethod(UUID id, PaymentMethod newMethod);
}