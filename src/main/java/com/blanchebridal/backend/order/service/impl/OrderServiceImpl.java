package com.blanchebridal.backend.order.service.impl;

import com.blanchebridal.backend.order.entity.OrderMode;
import com.blanchebridal.backend.payment.entity.PaymentMethod;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.dto.req.CreateOrderRequest;
import com.blanchebridal.backend.order.dto.req.OrderItemRequest;
import com.blanchebridal.backend.order.dto.res.OrderItemResponse;
import com.blanchebridal.backend.order.dto.res.OrderResponse;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderItem;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.order.service.OrderService;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req, UUID callerId, String role) {

        boolean isStaff = role != null &&
                (role.equals("ROLE_ADMIN") || role.equals("ADMIN") ||
                        role.equals("ROLE_EMPLOYEE") || role.equals("EMPLOYEE"));

        UUID targetUserId;
        if (isStaff) {
            if (req.getCustomerId() == null) {
                throw new IllegalArgumentException("customerId is required when creating an order as staff");
            }
            targetUserId = req.getCustomerId();
        } else {
            // CUSTOMER — always self, silently ignore any customerId in the request
            targetUserId = callerId;
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (isStaff && user.getRole() != UserRole.CUSTOMER) {
            throw new IllegalStateException("Orders can only be created for CUSTOMER accounts");
        }

        // ── Payment method resolution ───────────────────────────────────────
        // Defaults to PAYHERE if omitted. CARD is already rejected at the DTO
        // validation layer (CreateOrderRequest.isPaymentMethodValid), so only
        // PAYHERE/CASH reach here.
        PaymentMethod paymentMethod = req.getPaymentMethod() != null
                ? req.getPaymentMethod()
                : PaymentMethod.PAYHERE;

        if (paymentMethod == PaymentMethod.CASH && !isStaff) {
            throw new IllegalStateException(
                    "Cash payment is only available for staff-assisted orders");
        }

        // ... rest of method unchanged, but replace every subsequent
        // reference to `userId` with `targetUserId`, and `user` is already resolved above
        // (remove the old duplicate userRepository.findById(userId) line)

        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : req.getItems()) {
            // ... unchanged — this loop's contents were not shown to me.
            // DO NOT let this comment overwrite your real loop body when merging.
        }

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .notes(req.getNotes())
                .fulfillmentMethod(req.getFulfillmentMethod())
                .deliveryAddress(req.getDeliveryAddress())
                .customerPhone(req.getCustomerPhone())
                .orderMode(req.getOrderMode() != null ? req.getOrderMode() : OrderMode.WEBSITE)
                .paymentMethod(paymentMethod)
                .items(items)
                .build();

        items.forEach(item -> item.setOrder(order));

        Order saved = orderRepository.save(order);
        log.info("[Order] Created order {} for user {} (created by {}) — total LKR {} — payment method {}",
                saved.getId(), targetUserId, callerId, saved.getTotalAmount(), paymentMethod);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(OrderStatus status, Pageable pageable) {
        Page<Order> page = status != null
                ? orderRepository.findByStatus(status, pageable)
                : orderRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(UUID userId, Pageable pageable) {
        return orderRepository.findByUser_Id(userId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id, UUID requestingUserId, String role) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        boolean isCustomer = role != null &&
                (role.equals("ROLE_CUSTOMER") || role.equals("CUSTOMER"));

        if (isCustomer) {
            if (order.getUser() == null ||
                    !order.getUser().getId().equals(requestingUserId)) {
                throw new UnauthorizedException("Access denied to this order");
            }
        }

        return toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(UUID id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        log.info("[Order] Status updated — {} for order {}", newStatus, id);

        if (newStatus == OrderStatus.CONFIRMED) {
            try {
                User customer = saved.getUser();
                if (customer != null) {
                    List<String> itemSummaries = saved.getItems().stream()
                            .map(item -> item.getProductName()
                                    + " × " + item.getQuantity()
                                    + " — LKR " + item.getUnitPrice())
                            .collect(Collectors.toList());

                    emailService.sendOrderConfirmationEmail(
                            customer.getEmail(),
                            customer.getFirstName() + " " + customer.getLastName(),
                            saved.getId().toString().substring(0, 8).toUpperCase(),
                            saved.getTotalAmount(),
                            itemSummaries
                    );
                }
            } catch (Exception e) {
                log.warn("Failed to send order confirmation email for order {}: {}",
                        saved.getId(), e.getMessage());
            }
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void cancelOrder(UUID id, UUID userId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Access denied to this order");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("[Order] cancelOrder called on non-PENDING order {} (status: {}). Ignoring.",
                    id, order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("[Order] Cancelled order {} by user {}", id, userId);
    }

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
                .build();
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        BigDecimal subtotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return OrderItemResponse.builder()
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProductName())
                .productImage(item.getProductImage())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .size(item.getSize())
                .subtotal(subtotal)
                .build();
    }
}