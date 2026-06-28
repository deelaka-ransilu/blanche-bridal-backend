package com.blanchebridal.backend.order.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.order.dto.req.CreateOrderRequest;
import com.blanchebridal.backend.order.dto.req.OrderItemRequest;
import com.blanchebridal.backend.order.dto.res.OrderResponse;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtUtil jwtUtil;

    private CreateOrderRequest validCreateOrderRequest() {
        // OrderItemRequest is @Data with no declared constructor -> no-args + setters only
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(UUID.randomUUID());
        item.setQuantity(2);
        item.setSize("M");

        // CreateOrderRequest.items has @NotEmpty — must include at least one item
        // or this fails validation with 400 instead of reaching the controller logic.
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));
        request.setFulfillmentMethod("PICKUP");
        request.setOrderMode("ONLINE");
        return request;
    }

    // ── TC-OM-01: Admin can retrieve all orders ───────────────────────────────
    @Test
    @DisplayName("TC-OM-01: Admin can retrieve paginated list of all orders")
    @WithMockUser(roles = "ADMIN")
    void getAllOrders_asAdmin_returnsOrderList() throws Exception {
        OrderResponse mockOrder = OrderResponse.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .build();

        when(orderService.getAllOrders(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mockOrder)));

        mockMvc.perform(get("/api/orders")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.page").value(0));
    }

    // ── TC-OM-02: Customer cannot access all orders endpoint ─────────────────
    @Test
    @DisplayName("TC-OM-02: Customer accessing admin orders endpoint returns 403")
    @WithMockUser(roles = "CUSTOMER")
    void getAllOrders_asCustomer_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isForbidden());
    }

    // ── TC-OM-03: Customer can create an order with valid items ───────────────
    @Test
    @DisplayName("TC-OM-03: Authenticated customer can create a new order")
    @WithMockUser(roles = "CUSTOMER")
    void createOrder_asCustomer_returnsCreatedOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        CreateOrderRequest request = validCreateOrderRequest();

        OrderResponse mockResponse = OrderResponse.builder()
                .id(orderId)
                .status(OrderStatus.PENDING)
                .build();

        when(jwtUtil.extractUserId(any())).thenReturn(userId.toString());
        when(orderService.createOrder(any(CreateOrderRequest.class), eq(userId)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer mock.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderId.toString()));
    }

    // ── TC-OM-04: Unauthenticated user cannot create an order ────────────────
    // FLAGGED — status here is uncertain pending JwtFilter.java.
    // SecurityConfig's catch-all is `.anyRequest().authenticated()` with NO custom
    // AuthenticationEntryPoint configured. Spring Security's default in that case is
    // Http403ForbiddenEntryPoint -> 403, not 401, UNLESS JwtFilter itself short-circuits
    // and writes a 401 response before the filter chain's authorization check runs.
    // Asserting 403 as the most likely default for now — confirm against JwtFilter.java.
    @Test
    @DisplayName("TC-OM-04: Unauthenticated request to create order returns 403 (unverified — see comment)")
    void createOrder_withoutAuth_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // TC-OM-05: Admin can update order status
    @Test
    @DisplayName("TC-OM-05: Admin can update order status to CONFIRMED")
    @WithMockUser(roles = "ADMIN")
    void updateOrderStatus_asAdmin_returnsUpdatedOrder() throws Exception {
        UUID orderId = UUID.randomUUID();

        OrderResponse mockResponse = OrderResponse.builder()
                .id(orderId)
                .status(OrderStatus.CONFIRMED)
                .build();

        when(orderService.updateOrderStatus(eq(orderId), eq(OrderStatus.CONFIRMED)))
                .thenReturn(mockResponse);

        String body = """
                { "status": "CONFIRMED" }
                """;

        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    // ── TC-OM-06: Employee cannot update order status ─────────────────────────
    @Test
    @DisplayName("TC-OM-06: Employee accessing status update endpoint returns 403")
    @WithMockUser(roles = "EMPLOYEE")
    void updateOrderStatus_asEmployee_returnsForbidden() throws Exception {
        UUID orderId = UUID.randomUUID();
        String body = """
                { "status": "CONFIRMED" }
                """;

        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── TC-OM-07: Creating an order with an empty items list returns 400 ─────
    // CreateOrderRequest.items has @NotEmpty
    @Test
    @DisplayName("TC-OM-07: Creating an order with no items returns 400 Bad Request")
    @WithMockUser(roles = "CUSTOMER")
    void createOrder_withEmptyItems_returnsBadRequest() throws Exception {
        String body = """
                { "items": [] }
                """;

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer mock.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // TC-OM-08: Customer can retrieve their own order history
    @Test
    @DisplayName("TC-OM-08: Customer can retrieve their own paginated order list")
    @WithMockUser(roles = "CUSTOMER")
    void getMyOrders_asCustomer_returnsOwnOrders() throws Exception {
        UUID userId = UUID.randomUUID();
        OrderResponse mockOrder = OrderResponse.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .build();

        when(jwtUtil.extractUserId(any())).thenReturn(userId.toString());
        when(orderService.getMyOrders(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mockOrder)));

        mockMvc.perform(get("/api/orders/my")
                        .header("Authorization", "Bearer mock.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── TC-OM-09: Customer can retrieve a single order by id ──────────────────
    @Test
    @DisplayName("TC-OM-09: Customer can retrieve a single order by id")
    @WithMockUser(roles = "CUSTOMER")
    void getOrderById_asCustomer_returnsOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        OrderResponse mockResponse = OrderResponse.builder()
                .id(orderId)
                .status(OrderStatus.PENDING)
                .build();

        when(jwtUtil.extractUserId(any())).thenReturn(userId.toString());
        // @WithMockUser(roles = "CUSTOMER") grants authority "ROLE_CUSTOMER"
        when(orderService.getOrderById(eq(orderId), eq(userId), eq("ROLE_CUSTOMER")))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer mock.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderId.toString()));
    }

    // ── TC-OM-10: Customer can cancel their own order ─────────────────────────
    @Test
    @DisplayName("TC-OM-10: Customer can cancel their own order")
    @WithMockUser(roles = "CUSTOMER")
    void cancelOrder_asCustomer_returnsSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(jwtUtil.extractUserId(any())).thenReturn(userId.toString());
        doNothing().when(orderService).cancelOrder(eq(orderId), eq(userId));

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer mock.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}