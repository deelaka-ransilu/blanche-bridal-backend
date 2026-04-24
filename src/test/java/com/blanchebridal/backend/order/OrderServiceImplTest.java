package com.blanchebridal.backend.order;

import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.dto.req.CreateOrderRequest;
import com.blanchebridal.backend.order.dto.req.OrderItemRequest;
import com.blanchebridal.backend.order.dto.res.OrderResponse;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderItem;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.order.service.impl.OrderServiceImpl;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl Tests")
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User customer;
    private Product product;
    private Order order;
    private UUID customerId;
    private UUID productId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        productId  = UUID.randomUUID();
        orderId    = UUID.randomUUID();

        customer = User.builder()
                .id(customerId)
                .email("customer@example.com")
                .firstName("Amaya")
                .lastName("Silva")
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();

        product = Product.builder()
                .id(productId)
                .name("Ivory Lace Gown")
                .purchasePrice(new BigDecimal("45000.00"))
                .stock(5)
                .isAvailable(true)
                .images(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder()
                .productName("Ivory Lace Gown")
                .quantity(1)
                .unitPrice(new BigDecimal("45000.00"))
                .build();

        order = Order.builder()
                .id(orderId)
                .user(customer)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("45000.00"))
                .items(List.of(item))
                .build();
    }

    // ── createOrder ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("createOrder — creates order and deducts stock")
    void createOrder_validRequest_createsOrderAndDeductsStock() {
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(productId);
        itemReq.setQuantity(1);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(itemReq));

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderResponse response = orderService.createOrder(req, customerId);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("45000.00");
        verify(productRepository).save(any(Product.class)); // stock deducted
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder — throws ResourceNotFoundException when user not found")
    void createOrder_userNotFound_throwsException() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of());

        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(req, customerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("createOrder — throws ResourceNotFoundException when product not found")
    void createOrder_productNotFound_throwsException() {
        UUID unknownProductId = UUID.randomUUID();
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(unknownProductId);
        itemReq.setQuantity(1);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(itemReq));

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(unknownProductId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(req, customerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownProductId.toString());
    }

    @Test
    @DisplayName("createOrder — throws IllegalStateException when product unavailable")
    void createOrder_productUnavailable_throwsException() {
        product.setIsAvailable(false);

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(productId);
        itemReq.setQuantity(1);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(itemReq));

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.createOrder(req, customerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("createOrder — throws IllegalStateException when insufficient stock")
    void createOrder_insufficientStock_throwsException() {
        product.setStock(0);

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(productId);
        itemReq.setQuantity(1);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(itemReq));

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.createOrder(req, customerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");
    }

    // ── getOrderById ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrderById — admin can access any order")
    void getOrderById_adminRole_returnsOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(orderId, UUID.randomUUID(), "ROLE_ADMIN");

        assertThat(response.getId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("getOrderById — customer can access own order")
    void getOrderById_customerAccessingOwnOrder_returnsOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(orderId, customerId, "ROLE_CUSTOMER");

        assertThat(response.getId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("getOrderById — customer cannot access another customer's order")
    void getOrderById_customerAccessingOtherOrder_throwsException() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        UUID otherCustomerId = UUID.randomUUID();

        assertThatThrownBy(() ->
                orderService.getOrderById(orderId, otherCustomerId, "ROLE_CUSTOMER"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("getOrderById — throws ResourceNotFoundException when order not found")
    void getOrderById_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.getOrderById(unknownId, customerId, "ROLE_ADMIN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    // ── updateOrderStatus ─────────────────────────────────────────────────────

    @Test
    @DisplayName("updateOrderStatus — updates status to CONFIRMED and sends email")
    void updateOrderStatus_toConfirmed_updatesAndSendsEmail() {
        Order confirmed = Order.builder()
                .id(orderId)
                .user(customer)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("45000.00"))
                .items(order.getItems())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(confirmed);

        OrderResponse response = orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(emailService).sendOrderConfirmationEmail(
                eq(customer.getEmail()),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateOrderStatus — updates status to CANCELLED without sending email")
    void updateOrderStatus_toCancelled_doesNotSendEmail() {
        Order cancelled = Order.builder()
                .id(orderId)
                .user(customer)
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("45000.00"))
                .items(order.getItems())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(cancelled);

        OrderResponse response = orderService.updateOrderStatus(orderId, OrderStatus.CANCELLED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("updateOrderStatus — email failure does not prevent status update")
    void updateOrderStatus_emailFails_statusStillUpdated() {
        Order confirmed = Order.builder()
                .id(orderId)
                .user(customer)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("45000.00"))
                .items(order.getItems())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(confirmed);
        doThrow(new RuntimeException("SMTP error"))
                .when(emailService).sendOrderConfirmationEmail(any(), any(), any(), any(), any());

        OrderResponse response = orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("updateOrderStatus — throws ResourceNotFoundException when order not found")
    void updateOrderStatus_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.updateOrderStatus(unknownId, OrderStatus.CONFIRMED))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }
}