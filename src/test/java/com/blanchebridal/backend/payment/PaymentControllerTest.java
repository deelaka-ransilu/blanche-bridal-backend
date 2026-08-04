package com.blanchebridal.backend.payment;

import com.blanchebridal.backend.auth.security.JwtFilter;
import com.blanchebridal.backend.auth.security.JwtUtil;
import com.blanchebridal.backend.config.security.SecurityConfig;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.payment.controller.PaymentController;
import com.blanchebridal.backend.payment.dto.req.InitiatePaymentRequest;
import com.blanchebridal.backend.payment.dto.res.PaymentInitiateResponse;
import com.blanchebridal.backend.payment.dto.res.PaymentStatusResponse;
import com.blanchebridal.backend.payment.entity.PaymentStatus;
import com.blanchebridal.backend.payment.service.PaymentService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice tests for PaymentController.
 *
 * Like OrderController, PaymentController reads the caller from a real
 * @RequestHeader("Authorization") via JwtUtil.extractUserId(token) — NOT from the
 * Spring Security context — so authenticated tests need both an authentication()
 * post-processor (for @PreAuthorize) and a stubbed JwtUtil (for the caller id the
 * controller actually passes to PaymentService).
 *
 * SCOPE NOTE on the webhook tests: the guide's list for /notify includes hash-mismatch
 * handling, unknown order_id handling, and duplicate-webhook idempotency. All of that
 * logic lives inside PaymentServiceImpl.handleWebhook(), which is mocked away entirely
 * at this @WebMvcTest slice. PaymentController's only real, controller-level contract
 * for this endpoint is "always return 200, no matter what the service does or throws"
 * (PayHere retries on any non-200 response). The tests below verify exactly that
 * contract. The hash/idempotency/unknown-order-id business rules belong in a separate,
 * plain-Mockito PaymentServiceImplTest (no MockMvc, no HTTP layer) — not written here,
 * since the guide scoped this chat to the three *controllers*.
 */
@WebMvcTest(controllers = PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

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

    private PaymentInitiateResponse sampleInitiateResponse() {
        return PaymentInitiateResponse.builder()
                .merchantId("merchant123")
                .orderId(ORDER_ID.toString())
                .amount("15000.00")
                .currency("LKR")
                .hash("FAKEHASH")
                .itemsDescription("Blanche Bridal Order")
                .customerFirstName("Test")
                .customerLastName("Customer")
                .customerEmail("customer@test.com")
                .customerPhone("0771234567")
                .customerAddress("N/A")
                .customerCity("Colombo")
                .returnUrl("https://blanchebridal.com/checkout/success")
                .cancelUrl("https://blanchebridal.com/checkout/cancel")
                .notifyUrl("https://api.blanchebridal.com/api/payments/notify")
                .build();
    }

    // ─── A. Initiate payment ────────────────────────────────────────────────

    @Nested
    class InitiatePayment {

        @Test
        @DisplayName("TC-PAY-01: valid initiate request succeeds")
        void initiate_valid_returns200() throws Exception {
            stubCallerId(CUSTOMER_ID);
            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(ORDER_ID);
            when(paymentService.initiatePayment(ORDER_ID, CUSTOMER_ID)).thenReturn(sampleInitiateResponse());

            mockMvc.perform(post("/api/payments/initiate")
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderId").value(ORDER_ID.toString()))
                    .andExpect(jsonPath("$.data.hash").value("FAKEHASH"));
        }

        @Test
        @DisplayName("TC-PAY-02: null orderId returns 400")
        void initiate_nullOrderId_returns400() throws Exception {
            mockMvc.perform(post("/api/payments/initiate")
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("TC-PAY-03: malformed orderId UUID in the request body returns 400")
        void initiate_malformedOrderIdUuid_returns400() throws Exception {
            // Relies on the HttpMessageNotReadableException handler added to
            // GlobalExceptionHandler in this same pass — without it, this currently
            // returns 500 instead of 400.
            String malformedJson = "{\"orderId\":\"not-a-uuid\"}";

            mockMvc.perform(post("/api/payments/initiate")
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("TC-PAY-04: initiating payment for another user's order returns 401")
        void initiate_anotherUsersOrder_returns401() throws Exception {
            stubCallerId(CUSTOMER_ID);
            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(ORDER_ID);
            when(paymentService.initiatePayment(ORDER_ID, CUSTOMER_ID))
                    .thenThrow(new UnauthorizedException("Access denied to this order"));

            mockMvc.perform(post("/api/payments/initiate")
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-PAY-05: initiating payment as ADMIN (wrong role) returns 403")
        void initiate_asAdmin_returns403() throws Exception {
            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setOrderId(ORDER_ID);

            mockMvc.perform(post("/api/payments/initiate")
                            .with(asRole(ADMIN_ID, UserRole.ADMIN))
                            .header("Authorization", AUTH_HEADER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(paymentService);
        }
    }

    // ─── B. Webhook — controller-level contract only (see class-level note) ──

    @Nested
    class Webhook {

        @Test
        @DisplayName("TC-PAY-06: webhook delegates to the service and returns 200")
        void webhook_delegatesAndReturns200() throws Exception {
            mockMvc.perform(post("/api/payments/notify")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("merchant_id", "merchant123")
                            .param("order_id", ORDER_ID.toString())
                            .param("payhere_amount", "15000.00")
                            .param("payhere_currency", "LKR")
                            .param("status_code", "2")
                            .param("md5sig", "SOMEHASH")
                            .param("payment_id", "payhere-payment-id"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("OK"));

            verify(paymentService).handleWebhook(any());
        }

        @Test
        @DisplayName("TC-PAY-07: webhook still returns 200 even if the service throws — PayHere retries on any non-200")
        void webhook_serviceThrows_stillReturns200() throws Exception {
            doThrow(new RuntimeException("simulated failure inside handleWebhook"))
                    .when(paymentService).handleWebhook(any());

            mockMvc.perform(post("/api/payments/notify")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("merchant_id", "merchant123")
                            .param("order_id", ORDER_ID.toString())
                            .param("payhere_amount", "15000.00")
                            .param("payhere_currency", "LKR")
                            .param("status_code", "2")
                            .param("md5sig", "TAMPEREDHASH")
                            .param("payment_id", "payhere-payment-id"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("OK"));
        }

        @Test
        @DisplayName("TC-PAY-08: webhook requires no authentication (public endpoint)")
        void webhook_noAuthNeeded_returns200() throws Exception {
            // No .with(authentication(...)) at all — SecurityConfig permits this path
            // explicitly ("/api/payments/notify" is in the permitAll list).
            mockMvc.perform(post("/api/payments/notify")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("order_id", ORDER_ID.toString())
                            .param("status_code", "0"))
                    .andExpect(status().isOk());
        }
    }

    // ─── C. Get payment status ──────────────────────────────────────────────

    @Nested
    class GetPaymentStatus {

        @Test
        @DisplayName("TC-PAY-09: customer views their own order's payment status")
        void getStatus_ownOrder_returns200() throws Exception {
            stubCallerId(CUSTOMER_ID);
            when(paymentService.getPaymentStatus(ORDER_ID, CUSTOMER_ID, "ROLE_CUSTOMER"))
                    .thenReturn(PaymentStatusResponse.builder().status(PaymentStatus.COMPLETED.name()).build());

            mockMvc.perform(get("/api/payments/status/{orderId}", ORDER_ID)
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("TC-PAY-10: customer cannot view another customer's order status")
        void getStatus_anotherCustomersOrder_returns401() throws Exception {
            stubCallerId(CUSTOMER_ID);
            when(paymentService.getPaymentStatus(ORDER_ID, CUSTOMER_ID, "ROLE_CUSTOMER"))
                    .thenThrow(new UnauthorizedException("Access denied to this order"));

            mockMvc.perform(get("/api/payments/status/{orderId}", ORDER_ID)
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER))
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-PAY-11: admin can view any order's payment status")
        void getStatus_asAdmin_returns200() throws Exception {
            stubCallerId(ADMIN_ID);
            when(paymentService.getPaymentStatus(ORDER_ID, ADMIN_ID, "ROLE_ADMIN"))
                    .thenReturn(PaymentStatusResponse.builder().status(PaymentStatus.PENDING.name()).build());

            mockMvc.perform(get("/api/payments/status/{orderId}", ORDER_ID)
                            .with(asRole(ADMIN_ID, UserRole.ADMIN))
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("TC-PAY-12: employee can view any order's payment status")
        void getStatus_asEmployee_returns200() throws Exception {
            stubCallerId(EMPLOYEE_ID);
            when(paymentService.getPaymentStatus(ORDER_ID, EMPLOYEE_ID, "ROLE_EMPLOYEE"))
                    .thenReturn(PaymentStatusResponse.builder().status(PaymentStatus.PENDING.name()).build());

            mockMvc.perform(get("/api/payments/status/{orderId}", ORDER_ID)
                            .with(asRole(EMPLOYEE_ID, UserRole.EMPLOYEE))
                            .header("Authorization", AUTH_HEADER))
                    .andExpect(status().isOk());
        }
    }

    // ─── D. Confirm cash payment ────────────────────────────────────────────

    @Nested
    class ConfirmCashPayment {

        @Test
        @DisplayName("TC-PAY-13: admin confirms cash payment")
        void confirmCash_asAdmin_returns200() throws Exception {
            when(paymentService.confirmCashPayment(ORDER_ID))
                    .thenReturn(PaymentStatusResponse.builder().status(PaymentStatus.COMPLETED.name()).build());

            mockMvc.perform(post("/api/payments/{orderId}/confirm-cash", ORDER_ID)
                            .with(asRole(ADMIN_ID, UserRole.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("TC-PAY-14: employee is forbidden from confirming cash payment")
        void confirmCash_asEmployee_returns403() throws Exception {
            mockMvc.perform(post("/api/payments/{orderId}/confirm-cash", ORDER_ID)
                            .with(asRole(EMPLOYEE_ID, UserRole.EMPLOYEE)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("TC-PAY-15: customer is forbidden from confirming cash payment")
        void confirmCash_asCustomer_returns403() throws Exception {
            mockMvc.perform(post("/api/payments/{orderId}/confirm-cash", ORDER_ID)
                            .with(asRole(CUSTOMER_ID, UserRole.CUSTOMER)))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(paymentService);
        }
    }
}