package com.blanchebridal.backend.order;

import com.blanchebridal.backend.auth.security.JwtFilter;
import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.config.security.SecurityConfig;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.controller.OrderController;
import com.blanchebridal.backend.order.dto.req.CreateOrderRequest;
import com.blanchebridal.backend.order.dto.req.OrderItemRequest;
import com.blanchebridal.backend.order.dto.req.UpdateOrderStatusRequest;
import com.blanchebridal.backend.order.dto.res.OrderResponse;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.service.OrderService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.entity.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice tests for OrderController.
 *
 * Unlike AdminController, OrderController does NOT read the caller from the Spring
 * Security context — it takes a real @RequestHeader("Authorization") and calls
 * jwtUtil.extractUserId(token) directly. So each authenticated test here needs BOTH:
 *   1. .with(asRole(id, role))  — drives @PreAuthorize / hasRole checks
 *   2. a stubbed jwtUtil.extractUserId(TOKEN) + an actual Authorization header
 *      — drives which UUID the controller passes to OrderService as the caller
 *
 * KNOWN DEVIATION FROM THE GUIDE: the guide describes "Get all orders" as
 * admin-only with employee forbidden. The actual controller code is
 * @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')") — EMPLOYEE is allowed. Per the
 * "test CURRENT behavior only" instruction, the tests below assert what the code
 * actually does (ADMIN and EMPLOYEE both succeed, CUSTOMER forbidden), not what the
 * guide assumed.
 */
@WebMvcTest(controllers = OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private JwtFilter jwtFilter;

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String TOKEN = "test-jwt-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;

    @BeforeEach
    void makeJwtFilterPassThrough() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private User principal(UUID id, UserRole role) {
        return User.builder()
                .id(id)
                .email(role.name().toLowerCase() + "@test.com")
                .role(role)
                .status(UserStatus.ACTIVE)
                .firstName("Test")
                .lastName(role.name())
                .phone("0770000000")
                .build();
    }

    private RequestPostProcessor asRole(UUID id, UserRole role) {
        User user = principal(id, role);
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private void stubCallerId(UUID id) {
        when(jwtUtil.extractUserId(TOKEN)).thenReturn(id.toString());
    }

    private CreateOrderRequest validCreateOrderRequest() {
        CreateOrderRequest req = new CreateOrderRequest();
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(UUID.randomUUID());
        item.setQuantity(1);
        req.setItems(List.of(item));
        req.setFulfillmentMethod("PICKUP"); // avoids the deliveryAddress @AssertTrue requirement
        return req;
    }

    private OrderResponse sampleOrderResponse(UUID id) {
        return sampleOrderResponseWithStatus(id, OrderStatus.PENDING);
    }

    private OrderResponse sampleOrderResponseWithStatus(UUID id, OrderStatus status) {
        return OrderResponse.builder()
                .id(id)
                .status(status)
                .totalAmount(BigDecimal.valueOf(15000))
                .items(List.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .customerEmail("customer@test.com")
                .customerFirstName("Test")
                .customerLastName("Customer")
                .build();
    }

    // ─── A. Create order ────────────────────────────────────────────────────

    @Nested
    class CreateOrder {

        @Test
        @DisplayName("TC-OM-01: authenticated customer creates an order successfully")
        void createOrder_asCustomer_returns200() throws Exception {
            stubCallerId(CUSTOMER_ID);
            when(orderService.createOrder(any(), eq(CUSTOMER_ID), eq("ROLE_CUSTOMER")))
                    .thenReturn(sampleOrderResponse(ORDER_ID));

            mockMvc.perform(post("/api/orders")
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateOrderRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(ORDER_ID.toString()));
        }

        @Test
        @DisplayName("TC-OM-02: request with no Spring Security authentication established is forbidden")
        void createOrder_noAuthentication_returns403() throws Exception {
            // Deliberately does NOT use .with(asRole(...)) — SecurityConfig's default
            // AnonymousAuthenticationFilter still assigns ROLE_ANONYMOUS, which fails
            // hasAnyRole('CUSTOMER','ADMIN','EMPLOYEE') and throws AccessDeniedException
            // -> 403 (handled explicitly in GlobalExceptionHandler). The Authorization
            // header is still included so this test isolates the role-check failure
            // specifically, rather than the separate (and currently unhandled, same
            // category as the payment-body issue) MissingRequestHeaderException path
            // that a fully header-less request would hit.
            when(jwtUtil.extractUserId(TOKEN)).thenReturn(CUSTOMER_ID.toString());

            mockMvc.perform(post("/api/orders")
                            .header("Authorization", AUTH_HEADER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateOrderRequest())))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("TC-OM-03: empty items list is rejected with 400")
        void createOrder_emptyItems_returns400() throws Exception {
            stubCallerId(CUSTOMER_ID);
            CreateOrderRequest req = new CreateOrderRequest();
            req.setItems(List.of());
            req.setFulfillmentMethod("PICKUP");

            mockMvc.perform(post("/api/orders")
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }
    }

    // ─── B. Get own orders ──────────────────────────────────────────────────

    @Nested
    class GetMyOrders {

        @Test
        @DisplayName("TC-OM-04: customer retrieves own paginated order list")
        void getMyOrders_asCustomer_returns200() throws Exception {
            stubCallerId(CUSTOMER_ID);
            Page<OrderResponse> page = new PageImpl<>(List.of(sampleOrderResponse(ORDER_ID)));
            when(orderService.getMyOrders(eq(CUSTOMER_ID), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/orders/my")
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(ORDER_ID.toString()))
                    .andExpect(jsonPath("$.pagination.total").value(1));
        }
    }

    // ─── C. Get all orders ──────────────────────────────────────────────────

    @Nested
    class GetAllOrders {

        @Test
        @DisplayName("TC-OM-05: admin retrieves all orders")
        void getAllOrders_asAdmin_returns200() throws Exception {
            Page<OrderResponse> page = new PageImpl<>(List.of(sampleOrderResponse(ORDER_ID)));
            when(orderService.getAllOrders(isNull(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/orders").with(asRole(ADMIN_ID, UserRole.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(ORDER_ID.toString()));
        }

        @Test
        @DisplayName("TC-OM-06: employee also retrieves all orders (see class-level deviation note)")
        void getAllOrders_asEmployee_returns200() throws Exception {
            Page<OrderResponse> page = new PageImpl<>(List.of(sampleOrderResponse(ORDER_ID)));
            when(orderService.getAllOrders(isNull(), any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/orders").with(asRole(EMPLOYEE_ID, UserRole.EMPLOYEE)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TC-OM-07: customer is forbidden from listing all orders")
        void getAllOrders_asCustomer_returns403() throws Exception {
            mockMvc.perform(get("/api/orders").with(asRole(CUSTOMER_ID, UserRole.CUSTOMER)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(orderService);
        }
    }

    // ─── D. Update order status ─────────────────────────────────────────────

    @Nested
    class UpdateOrderStatus {

        private UpdateOrderStatusRequest confirmedRequest() {
            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.CONFIRMED);
            return req;
        }

        @Test
        @DisplayName("TC-OM-08: admin updates order status to CONFIRMED")
        void updateOrderStatus_asAdmin_returns200() throws Exception {
            when(orderService.updateOrderStatus(ORDER_ID, OrderStatus.CONFIRMED))
                    .thenReturn(sampleOrderResponseWithStatus(ORDER_ID, OrderStatus.CONFIRMED));

            mockMvc.perform(put("/api/orders/{id}/status", ORDER_ID)
                            .with(asRole(ADMIN_ID, UserRole.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(confirmedRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        }

        @Test
        @DisplayName("TC-OM-09: employee is forbidden from updating order status")
        void updateOrderStatus_asEmployee_returns403() throws Exception {
            mockMvc.perform(put("/api/orders/{id}/status", ORDER_ID)
                            .with(asRole(EMPLOYEE_ID, UserRole.EMPLOYEE))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(confirmedRequest())))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("TC-OM-10: customer is forbidden from updating order status")
        void updateOrderStatus_asCustomer_returns403() throws Exception {
            mockMvc.perform(put("/api/orders/{id}/status", ORDER_ID)
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(confirmedRequest())))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(orderService);
        }
    }

    // ─── E. Cancel order ─────────────────────────────────────────────────────

    @Nested
    class CancelOrder {

        @Test
        @DisplayName("TC-OM-11: customer cancels their own order")
        void cancelOrder_ownOrder_returns200() throws Exception {
            stubCallerId(CUSTOMER_ID);
            doNothing().when(orderService).cancelOrder(ORDER_ID, CUSTOMER_ID);

            mockMvc.perform(post("/api/orders/{id}/cancel", ORDER_ID)
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(orderService).cancelOrder(ORDER_ID, CUSTOMER_ID);
        }

        @Test
        @DisplayName("TC-OM-12: customer cannot cancel another customer's order")
        void cancelOrder_anotherCustomersOrder_returns401() throws Exception {
            stubCallerId(CUSTOMER_ID);
            doThrow(new UnauthorizedException("Access denied to this order"))
                    .when(orderService).cancelOrder(ORDER_ID, CUSTOMER_ID);

            mockMvc.perform(post("/api/orders/{id}/cancel", ORDER_ID)
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── F. Get order by id (ownership check, independent of role check) ───

    @Nested
    class GetOrderById {

        @Test
        @DisplayName("TC-OM-13: customer retrieves their own order by id")
        void getOrderById_ownOrder_returns200() throws Exception {
            stubCallerId(CUSTOMER_ID);
            when(orderService.getOrderById(ORDER_ID, CUSTOMER_ID, "ROLE_CUSTOMER"))
                    .thenReturn(sampleOrderResponse(ORDER_ID));

            mockMvc.perform(get("/api/orders/{id}", ORDER_ID)
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(ORDER_ID.toString()));
        }

        @Test
        @DisplayName("TC-OM-14: customer requesting another customer's order is rejected, independent of role check")
        void getOrderById_anotherCustomersOrder_returns401() throws Exception {
            // The role check (hasAnyRole CUSTOMER/ADMIN/EMPLOYEE) PASSES here — CUSTOMER
            // is an allowed role for this endpoint. The rejection comes entirely from the
            // ownership check inside OrderServiceImpl.getOrderById(), which is why this is
            // a 401 (UnauthorizedException) and not a 403 (AccessDeniedException).
            stubCallerId(CUSTOMER_ID);
            when(orderService.getOrderById(ORDER_ID, CUSTOMER_ID, "ROLE_CUSTOMER"))
                    .thenThrow(new UnauthorizedException("Access denied to this order"));

            mockMvc.perform(get("/api/orders/{id}", ORDER_ID)
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-OM-15: admin can retrieve any order by id (ownership check only applies to customers)")
        void getOrderById_asAdmin_returns200() throws Exception {
            stubCallerId(ADMIN_ID);
            when(orderService.getOrderById(ORDER_ID, ADMIN_ID, "ROLE_ADMIN"))
                    .thenReturn(sampleOrderResponse(ORDER_ID));

            mockMvc.perform(get("/api/orders/{id}", ORDER_ID)
                            .with(asRole(ADMIN_ID, UserRole.ADMIN))
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk());
        }
    }
}