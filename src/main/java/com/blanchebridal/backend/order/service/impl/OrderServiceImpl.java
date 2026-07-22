package com.blanchebridal.backend.order.service.impl;

import com.blanchebridal.backend.order.entity.DiscountType;
import com.blanchebridal.backend.order.entity.OrderMode;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.payment.entity.Payment;
import com.blanchebridal.backend.payment.entity.PaymentMethod;
import com.blanchebridal.backend.payment.entity.PaymentStatus;
import com.blanchebridal.backend.payment.repository.PaymentRepository;
import com.blanchebridal.backend.refund.entity.Refund;
import com.blanchebridal.backend.refund.repository.RefundBankDetailsRepository;
import com.blanchebridal.backend.refund.repository.RefundRepository;
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
import com.blanchebridal.backend.order.service.OrderService;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.repository.UserRepository;
import com.blanchebridal.backend.appointment.repository.CustomDesignRequestRepository;
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
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundBankDetailsRepository refundBankDetailsRepository;
    private final CustomDesignRequestRepository customDesignRequestRepository;
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

        // ── Discount resolution (staff-only, FR-OM-11) ──────────────────────
        DiscountType discountType = null;
        BigDecimal discountValue = null;
        String discountReason = null;

        if (req.getDiscountType() != null) {
            if (!isStaff) {
                throw new IllegalStateException("Discounts can only be applied by staff");
            }
            if (req.getDiscountType() == DiscountType.PERCENTAGE
                    && (req.getDiscountValue().compareTo(BigDecimal.ZERO) < 0
                    || req.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0)) {
                throw new IllegalArgumentException("Percentage discount must be between 0 and 100");
            }
            discountType = req.getDiscountType();
            discountValue = req.getDiscountValue();
            discountReason = req.getDiscountReason();
        }

        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : req.getItems()) {
            // Locked read — without this, two near-simultaneous orders on the
            // last unit of stock can both pass the check below before either
            // commits its decrement, causing an oversell. The lock is held
            // for the rest of this transaction and released on commit.
            Product product = productRepository.findByIdForUpdate(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found: " + itemReq.getProductId()));

            if (Boolean.FALSE.equals(product.getIsActive()) || Boolean.FALSE.equals(product.getIsAvailable())) {
                throw new IllegalStateException("Product is not available: " + product.getName());
            }

            if (product.getStock() < itemReq.getQuantity()) {
                throw new IllegalStateException(
                        "Insufficient stock for " + product.getName()
                                + " — available: " + product.getStock()
                                + ", requested: " + itemReq.getQuantity());
            }

            // Reserve stock now, at order creation (PENDING), not at
            // CONFIRMED. Reserving later leaves the same oversell window
            // open between "order created" and "staff confirms" — two
            // customers could both pass the check above and only one could
            // actually be confirmed. Abandoned/cancelled orders release
            // their reservation back via cancelOrder (and, later, an
            // expiry job for stale PENDING_PAYMENT orders using the same
            // restore path).
            product.setStock(product.getStock() - itemReq.getQuantity());
            productRepository.save(product);

            // NOTE: purchase-only for now — no lineType field exists yet to choose
            // between purchasePrice/rentalPrice. This is Payment Issue A
            // (CURRENT_STATE.md #1). Defaulting to purchasePrice.
            BigDecimal unitPrice = product.getPurchasePrice() != null
                    ? product.getPurchasePrice()
                    : BigDecimal.ZERO;

            String imageUrl = (product.getImages() != null && !product.getImages().isEmpty())
                    ? product.getImages().get(0).getUrl() // ⚠ confirm actual getter on ProductImage
                    : null;

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .size(itemReq.getSize())
                    .productName(product.getName())
                    .productImage(imageUrl)
                    .build();

            items.add(item);
            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }

        // ── Apply discount to total (if any) ────────────────────────────────
        BigDecimal discountedTotal = totalAmount;
        if (discountType != null) {
            BigDecimal discountAmount = discountType == DiscountType.PERCENTAGE
                    ? totalAmount.multiply(discountValue).divide(BigDecimal.valueOf(100))
                    : discountValue;

            if (discountAmount.compareTo(totalAmount) > 0) {
                throw new IllegalArgumentException("Discount cannot exceed order total");
            }
            discountedTotal = totalAmount.subtract(discountAmount);
        }

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .totalAmount(discountedTotal)
                .notes(req.getNotes())
                .fulfillmentMethod(req.getFulfillmentMethod())
                .deliveryAddress(req.getDeliveryAddress())
                .customerPhone(req.getCustomerPhone())
                .orderMode(req.getOrderMode() != null ? req.getOrderMode() : OrderMode.WEBSITE)
                .paymentMethod(paymentMethod)
                .discountType(discountType)
                .discountValue(discountValue)
                .discountReason(discountReason)
                .items(items)
                .build();

        items.forEach(item -> item.setOrder(order));

        Order saved = orderRepository.save(order);
        log.info("[Order] Created order {} for user {} (created by {}) — total LKR {} (discount: {}) — payment method {}",
                saved.getId(), targetUserId, callerId, saved.getTotalAmount(), discountType, paymentMethod);
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

        OrderStatus previousStatus = order.getStatus();

        // Terminal states don't transition anywhere — a cancelled or completed
        // order is done. Re-requesting the same terminal status is a harmless
        // no-op (idempotent double-click), but moving OUT of one is not allowed.
        if (isTerminal(previousStatus) && newStatus != previousStatus) {
            throw new IllegalStateException(
                    "Order " + id + " is " + previousStatus + " and cannot be changed to " + newStatus);
        }

        // Restore reserved stock if staff cancels via this path too — not
        // just the customer-facing cancelOrder() below. Without this, a
        // staff-side cancel leaks the reservation forever.
        if (newStatus == OrderStatus.CANCELLED && previousStatus != OrderStatus.CANCELLED) {
            restoreStock(order);
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        log.info("[Order] Status updated — {} for order {}", newStatus, id);

        if (newStatus != previousStatus) {
            if (newStatus == OrderStatus.CONFIRMED) {
                sendConfirmationEmailSafely(saved);
            } else if (newStatus == OrderStatus.READY) {
                sendReadyEmailSafely(saved);
            } else if (newStatus == OrderStatus.CANCELLED) {
                sendCancelledEmailSafely(saved);
            }
        }

        return toResponse(saved);
    }

    // Terminal = no further transitions allowed out of this state.
    private boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.CANCELLED || status == OrderStatus.COMPLETED;
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

        restoreStock(order);

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        log.info("[Order] Cancelled order {} by user {}", id, userId);

        sendCancelledEmailSafely(saved);
    }

    @Override
    @Transactional
    public OrderResponse updatePaymentMethod(UUID id, PaymentMethod newMethod) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Payment method can only be changed while the order is PENDING — current status: "
                            + order.getStatus());
        }

        // A CASH switch only makes sense if no payment attempt has started —
        // if a PayHere Payment row already exists (even PENDING, from a customer
        // who opened the checkout form), switching underneath it would orphan
        // that row. Block the switch in that case; admin should let it expire
        // or the flow needs a separate "void existing payment" step first.
        paymentRepository.findByOrder_Id(id).ifPresent(p -> {
            if (p.getStatus() == PaymentStatus.COMPLETED) {
                throw new IllegalStateException("Cannot change payment method — payment already completed");
            }
        });

        PaymentMethod previous = order.getPaymentMethod();
        order.setPaymentMethod(newMethod);
        Order saved = orderRepository.save(order);
        log.info("[Order] Payment method changed for order {}: {} -> {}", id, previous, newMethod);
        return toResponse(saved);
    }

    // Gives back the stock reserved at createOrder time. Shared by both
    // cancelOrder (customer-initiated) and updateOrderStatus (staff-initiated
    // cancel), and reusable later by a stale-order expiry job — same
    // reservation, same release path, no matter who/what triggers it.
    private void restoreStock(Order order) {
        if (order.getItems() == null) return;
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null) continue;
            // Locked read for the same reason as createOrder — restoring
            // stock is itself a read-modify-write that needs to be race-safe.
            Product locked = productRepository.findByIdForUpdate(product.getId())
                    .orElse(null);
            if (locked == null) continue;
            locked.setStock(locked.getStock() + item.getQuantity());
            productRepository.save(locked);
        }
    }

    // ─── Email hooks ────────────────────────────────────────────────────────
    // Each is wrapped in try/catch so an email failure never rolls back or
    // interrupts the status-change transaction it's called from.

    private void sendConfirmationEmailSafely(Order order) {
        try {
            User customer = order.getUser();
            if (customer == null || customer.getEmail() == null) return;

            List<String> itemSummaries = order.getItems().stream()
                    .map(item -> item.getProductName()
                            + " × " + item.getQuantity()
                            + " — LKR " + item.getUnitPrice())
                    .collect(Collectors.toList());

            emailService.sendOrderConfirmationEmail(
                    customer.getEmail(),
                    customer.getFirstName() + " " + customer.getLastName(),
                    order.getId().toString().substring(0, 8).toUpperCase(),
                    order.getTotalAmount(),
                    itemSummaries
            );
        } catch (Exception e) {
            log.warn("Failed to send order confirmation email for order {}: {}",
                    order.getId(), e.getMessage());
        }
    }

    private void sendReadyEmailSafely(Order order) {
        try {
            User customer = order.getUser();
            if (customer == null || customer.getEmail() == null) return;

            String fulfillmentMethod = order.getFulfillmentMethod() != null
                    ? order.getFulfillmentMethod()
                    : "PICKUP";

            emailService.sendOrderReadyEmail(
                    customer.getEmail(),
                    customer.getFirstName() + " " + customer.getLastName(),
                    order.getId().toString().substring(0, 8).toUpperCase(),
                    fulfillmentMethod
            );
        } catch (Exception e) {
            log.warn("Failed to send order-ready email for order {}: {}",
                    order.getId(), e.getMessage());
        }
    }

    private void sendCancelledEmailSafely(Order order) {
        try {
            User customer = order.getUser();
            if (customer == null || customer.getEmail() == null) return;

            boolean refundOwed = paymentRepository.findByOrder_Id(order.getId())
                    .map(p -> p.getStatus() == PaymentStatus.COMPLETED)
                    .orElse(false);

            String customerName = (customer.getFirstName() != null ? customer.getFirstName() : "")
                    + (customer.getLastName() != null ? " " + customer.getLastName() : "");
            customerName = customerName.isBlank() ? "Customer" : customerName.trim();

            emailService.sendOrderCancelledEmail(
                    order.getUser().getEmail(),
                    customerName,
                    order.getId().toString(),   // full UUID, not truncated
                    refundOwed
            );
        } catch (Exception e) {
            log.warn("Failed to send order-cancelled email for order {}: {}",
                    order.getId(), e.getMessage());
        }
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems() == null
                ? List.of()
                : order.getItems().stream().map(this::toItemResponse).toList();

        String email = order.getUser() != null ? order.getUser().getEmail() : null;
        String firstName = order.getUser() != null ? order.getUser().getFirstName() : null;
        String lastName = order.getUser() != null ? order.getUser().getLastName() : null;

        Payment payment = paymentRepository.findByOrder_Id(order.getId()).orElse(null);
        Refund refund = refundRepository.findByOrder_Id(order.getId()).orElse(null);
        boolean bankDetailsSubmitted = refundBankDetailsRepository.existsByOrder_Id(order.getId());

        // Same lookup pattern as ProductionStageRecordServiceImpl.fireStageEmail —
        // an Order may be linked as either the first- or second-payment order
        // for a CustomDesignRequest, so try both FK directions.
        UUID customDesignRequestId = customDesignRequestRepository
                .findByFirstPaymentOrder_Id(order.getId())
                .or(() -> customDesignRequestRepository.findBySecondPaymentOrder_Id(order.getId()))
                .map(cdr -> cdr.getId())
                .orElse(null);

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
                .paymentStatus(payment != null ? payment.getStatus() : null)
                .refundAmount(refund != null ? refund.getAmount() : null)
                .refundedAt(refund != null ? refund.getCreatedAt() : null)
                .refundProofImageUrl(refund != null ? refund.getProofImageUrl() : null)
                .bankDetailsSubmitted(bankDetailsSubmitted)
                .customDesignRequestId(customDesignRequestId)
                .build();
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        BigDecimal subtotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return OrderItemResponse.builder()
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProductName())
                .productImage(item.getProductImage())
                .productType(item.getProduct() != null ? item.getProduct().getType() : null)  // ← ADD
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .size(item.getSize())
                .subtotal(subtotal)
                .build();
    }
}