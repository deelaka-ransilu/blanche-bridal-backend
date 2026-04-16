package com.blanchebridal.backend.order.service;

import com.blanchebridal.backend.order.dto.req.CreateOrderRequest;
import com.blanchebridal.backend.order.dto.res.OrderResponse;
import com.blanchebridal.backend.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest req, UUID userId);
    Page<OrderResponse> getAllOrders(OrderStatus status, Pageable pageable);
    Page<OrderResponse> getMyOrders(UUID userId, Pageable pageable);
    OrderResponse getOrderById(UUID id, UUID requestingUserId, String role);
    OrderResponse updateOrderStatus(UUID id, OrderStatus newStatus);
}