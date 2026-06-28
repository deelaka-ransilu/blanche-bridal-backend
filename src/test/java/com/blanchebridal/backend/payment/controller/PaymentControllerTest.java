package com.blanchebridal.backend.payment.controller;

import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.payment.dto.req.InitiatePaymentRequest;
import com.blanchebridal.backend.payment.dto.res.PaymentInitiateResponse;
import com.blanchebridal.backend.payment.dto.res.PaymentStatusResponse;
import com.blanchebridal.backend.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private static final UUID TEST_ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEST_USER_ID  = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BEARER_TOKEN = "Bearer mock.jwt.token";
    private static final String RAW_TOKEN    = "mock.jwt.token";

    // ── TC-PAY-01: Successful payment initiation ──────────────────────────────
    @Test
    @DisplayName("TC-PAY-01: Initiate payment with valid request returns payment form fields")
    @WithMockUser(roles = "CUSTOMER")
    void initiatePayment_withValidRequest_returnsPaymentData() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setOrderId(TEST_ORDER_ID);

        when(jwtUtil.extractUserId(RAW_TOKEN)).thenReturn(TEST_USER_ID.toString());
        when(paymentService.initiatePayment(eq(TEST_ORDER_ID), eq(TEST_USER_ID)))
                .thenReturn(buildInitiateResponse());

        mockMvc.perform(post("/api/payments/initiate")
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    // ── TC-PAY-02: Initiate payment as ADMIN returns 403 ─────────────────────
    // @PreAuthorize("hasRole('CUSTOMER')") rejects non-CUSTOMER roles.
    // GlobalExceptionHandler maps AccessDeniedException → 403.
    @Test
    @DisplayName("TC-PAY-02: Initiate payment as ADMIN returns 403 Forbidden")
    @WithMockUser(roles = "ADMIN")
    void initiatePayment_asAdmin_returnsForbidden() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setOrderId(TEST_ORDER_ID);

        mockMvc.perform(post("/api/payments/initiate")
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // TC-PAY-03: Initiate payment with null orderId returns 400
    // @NotNull on InitiatePaymentRequest.orderId triggers MethodArgumentNotValidException.
    @Test
    @DisplayName("TC-PAY-03: Initiate payment with null orderId returns 400 Bad Request")
    @WithMockUser(roles = "CUSTOMER")
    void initiatePayment_withNullOrderId_returnsBadRequest() throws Exception {
        when(jwtUtil.extractUserId(RAW_TOKEN)).thenReturn(TEST_USER_ID.toString());

        mockMvc.perform(post("/api/payments/initiate")
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── TC-PAY-04: Initiate payment — order not found returns 404 ────────────
    @Test
    @DisplayName("TC-PAY-04: Initiate payment for non-existent order returns 404")
    @WithMockUser(roles = "CUSTOMER")
    void initiatePayment_orderNotFound_returnsNotFound() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setOrderId(TEST_ORDER_ID);

        when(jwtUtil.extractUserId(RAW_TOKEN)).thenReturn(TEST_USER_ID.toString());
        when(paymentService.initiatePayment(any(UUID.class), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Order not found"));

        mockMvc.perform(post("/api/payments/initiate")
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── TC-PAY-05: Initiate payment — order owned by another user returns 401 ─
    @Test
    @DisplayName("TC-PAY-05: Initiate payment for another user's order returns 401")
    @WithMockUser(roles = "CUSTOMER")
    void initiatePayment_orderBelongsToOtherUser_returnsUnauthorized() throws Exception {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setOrderId(TEST_ORDER_ID);

        when(jwtUtil.extractUserId(RAW_TOKEN)).thenReturn(TEST_USER_ID.toString());
        when(paymentService.initiatePayment(any(UUID.class), any(UUID.class)))
                .thenThrow(new UnauthorizedException("You do not own this order"));

        mockMvc.perform(post("/api/payments/initiate")
                        .header("Authorization", BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    // ── TC-PAY-06: Webhook — successful notification returns 200 "OK" ─────────
    // The notify endpoint is public (SecurityConfig permitAll) and MUST always
    // return HTTP 200 — PayHere retries on any other status.
    @Test
    @DisplayName("TC-PAY-06: Valid webhook notification is processed and returns 200 OK")
    void handleWebhook_withValidParams_returnsOk() throws Exception {
        doNothing().when(paymentService).handleWebhook(any());

        mockMvc.perform(post("/api/payments/notify")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("order_id", TEST_ORDER_ID.toString())
                        .param("status_code", "2")
                        .param("payment_id", "320027785220")
                        .param("payhere_amount", "1500.00")
                        .param("payhere_currency", "LKR")
                        .param("md5sig", "mock-signature"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // ── TC-PAY-07: Webhook swallows service exceptions and still returns 200 ──
    // Controller catches all exceptions internally — never propagates to PayHere.
    @Test
    @DisplayName("TC-PAY-07: Webhook returns 200 even when service throws an exception")
    void handleWebhook_serviceThrows_stillReturnsOk() throws Exception {
        doThrow(new RuntimeException("DB connection lost"))
                .when(paymentService).handleWebhook(any());

        mockMvc.perform(post("/api/payments/notify")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("order_id", TEST_ORDER_ID.toString())
                        .param("status_code", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // ── TC-PAY-08: Webhook with no params still returns 200 ──────────────────
    @Test
    @DisplayName("TC-PAY-08: Webhook with empty params still returns 200")
    void handleWebhook_withEmptyParams_returnsOk() throws Exception {
        doNothing().when(paymentService).handleWebhook(any());

        mockMvc.perform(post("/api/payments/notify")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // ── TC-PAY-09: Get payment status — successful poll ───────────────────────
    @Test
    @DisplayName("TC-PAY-09: Get payment status with valid orderId returns status data")
    @WithMockUser(roles = "CUSTOMER")
    void getPaymentStatus_withValidOrder_returnsStatus() throws Exception {
        when(jwtUtil.extractUserId(RAW_TOKEN)).thenReturn(TEST_USER_ID.toString());
        when(paymentService.getPaymentStatus(eq(TEST_ORDER_ID), eq(TEST_USER_ID)))
                .thenReturn(PaymentStatusResponse.builder().status("COMPLETED").build());

        mockMvc.perform(get("/api/payments/status/{orderId}", TEST_ORDER_ID)
                        .header("Authorization", BEARER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    // ── TC-PAY-10: Get payment status as ADMIN returns 403 ───────────────────
    @Test
    @DisplayName("TC-PAY-10: Get payment status as ADMIN returns 403 Forbidden")
    @WithMockUser(roles = "ADMIN")
    void getPaymentStatus_asAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/payments/status/{orderId}", TEST_ORDER_ID)
                        .header("Authorization", BEARER_TOKEN))
                .andExpect(status().isForbidden());
    }

    // ── TC-PAY-11: Get payment status — order not found returns 404 ──────────
    @Test
    @DisplayName("TC-PAY-11: Get payment status for non-existent order returns 404")
    @WithMockUser(roles = "CUSTOMER")
    void getPaymentStatus_orderNotFound_returnsNotFound() throws Exception {
        when(jwtUtil.extractUserId(RAW_TOKEN)).thenReturn(TEST_USER_ID.toString());
        when(paymentService.getPaymentStatus(any(UUID.class), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Order not found"));

        mockMvc.perform(get("/api/payments/status/{orderId}", TEST_ORDER_ID)
                        .header("Authorization", BEARER_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── TC-PAY-12: Get payment status — unauthorized user returns 401 ─────────
    @Test
    @DisplayName("TC-PAY-12: Get payment status for another user's order returns 401")
    @WithMockUser(roles = "CUSTOMER")
    void getPaymentStatus_orderBelongsToOtherUser_returnsUnauthorized() throws Exception {
        when(jwtUtil.extractUserId(RAW_TOKEN)).thenReturn(TEST_USER_ID.toString());
        when(paymentService.getPaymentStatus(any(UUID.class), any(UUID.class)))
                .thenThrow(new UnauthorizedException("You do not own this order"));

        mockMvc.perform(get("/api/payments/status/{orderId}", TEST_ORDER_ID)
                        .header("Authorization", BEARER_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── TC-PAY-13: Malformed UUID in path returns 400 ─────────────────────────
    @Test
    @DisplayName("TC-PAY-13: Get payment status with malformed UUID returns 400")
    @WithMockUser(roles = "CUSTOMER")
    void getPaymentStatus_malformedUuid_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/payments/status/not-a-uuid")
                        .header("Authorization", BEARER_TOKEN))
                .andExpect(status().isBadRequest());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PaymentInitiateResponse buildInitiateResponse() {
        return PaymentInitiateResponse.builder()
                .merchantId("merchant-123")
                .orderId(TEST_ORDER_ID.toString())
                .amount("1500.00")
                .currency("LKR")
                .hash("mock-hash-value")
                .itemsDescription("Order items")
                .customerFirstName("Jane")
                .customerLastName("Doe")
                .customerEmail("jane@example.com")
                .customerPhone("0771234567")
                .customerAddress("123 Main St")
                .customerCity("Colombo")
                .returnUrl("https://example.com/return")
                .cancelUrl("https://example.com/cancel")
                .notifyUrl("https://example.com/notify")
                .build();
    }
}