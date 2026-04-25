package com.blanchebridal.backend.payment;

import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderItem;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.payment.dto.res.PaymentInitiateResponse;
import com.blanchebridal.backend.payment.dto.res.PaymentStatusResponse;
import com.blanchebridal.backend.payment.entity.Payment;
import com.blanchebridal.backend.payment.entity.PaymentMethod;
import com.blanchebridal.backend.payment.entity.PaymentStatus;
import com.blanchebridal.backend.payment.entity.Receipt;
import com.blanchebridal.backend.payment.repository.PaymentRepository;
import com.blanchebridal.backend.payment.service.ReceiptService;
import com.blanchebridal.backend.payment.service.impl.PaymentServiceImpl;
import com.blanchebridal.backend.payment.util.PayHereUtil;
import com.blanchebridal.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl")
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository   orderRepository;
    @Mock private PayHereUtil       payHereUtil;
    @Mock private ReceiptService    receiptService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID USER_ID  = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();

    private User    customer;
    private Order   order;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        // inject @Value fields
        ReflectionTestUtils.setField(paymentService, "merchantId",  "test_merchant");
        ReflectionTestUtils.setField(paymentService, "returnUrl",   "http://localhost:3000/checkout/success");
        ReflectionTestUtils.setField(paymentService, "cancelUrl",   "http://localhost:3000/checkout/cancel");
        ReflectionTestUtils.setField(paymentService, "notifyUrl",   "http://localhost:8080/api/payments/notify");

        customer = User.builder()
                .id(USER_ID)
                .email("customer@test.com")
                .firstName("Alice")
                .lastName("Silva")
                .build();

        OrderItem item = OrderItem.builder()
                .productName("Ivory Gown")
                .quantity(1)
                .unitPrice(new BigDecimal("15000.00"))
                .build();

        order = Order.builder()
                .id(ORDER_ID)
                .user(customer)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("15000.00"))
                .items(new ArrayList<>(List.of(item)))
                .build();

        pendingPayment = Payment.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .method(PaymentMethod.PAYHERE)
                .status(PaymentStatus.PENDING)
                .payhereOrderId(ORDER_ID.toString())
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  initiatePayment
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("initiatePayment()")
    class InitiatePayment {

        @Test
        @DisplayName("returns PaymentInitiateResponse with hash and customer details")
        void happyPath() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrder_Id(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentRepository.save(any())).thenReturn(pendingPayment);
            when(payHereUtil.generateHash(eq("test_merchant"), eq(ORDER_ID.toString()),
                    eq("15000.00"), eq("LKR")))
                    .thenReturn("ABC123HASH");

            PaymentInitiateResponse res = paymentService.initiatePayment(ORDER_ID, USER_ID);

            assertThat(res.getMerchantId()).isEqualTo("test_merchant");
            assertThat(res.getOrderId()).isEqualTo(ORDER_ID.toString());
            assertThat(res.getAmount()).isEqualTo("15000.00");
            assertThat(res.getCurrency()).isEqualTo("LKR");
            assertThat(res.getHash()).isEqualTo("ABC123HASH");
            assertThat(res.getCustomerEmail()).isEqualTo("customer@test.com");
            assertThat(res.getCustomerFirstName()).isEqualTo("Alice");
            assertThat(res.getCustomerLastName()).isEqualTo("Silva");
            assertThat(res.getNotifyUrl()).isEqualTo("http://localhost:8080/api/payments/notify");
        }

        @Test
        @DisplayName("reuses existing PENDING payment record instead of creating a new one")
        void reusesExistingPayment() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrder_Id(ORDER_ID)).thenReturn(Optional.of(pendingPayment));
            when(paymentRepository.save(any())).thenReturn(pendingPayment);
            when(payHereUtil.generateHash(any(), any(), any(), any())).thenReturn("HASH");

            paymentService.initiatePayment(ORDER_ID, USER_ID);

            // save called once — for the existing record, not a newly-built one
            verify(paymentRepository, times(1)).save(pendingPayment);
        }

        @Test
        @DisplayName("builds items description with 'and N more' when order has multiple items")
        void itemsDescriptionMultipleItems() {
            OrderItem item2 = OrderItem.builder()
                    .productName("Veil")
                    .quantity(1)
                    .unitPrice(new BigDecimal("3000.00"))
                    .build();
            order.getItems().add(item2); // mutate list is fine in tests

            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrder_Id(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentRepository.save(any())).thenReturn(pendingPayment);
            when(payHereUtil.generateHash(any(), any(), any(), any())).thenReturn("HASH");

            PaymentInitiateResponse res = paymentService.initiatePayment(ORDER_ID, USER_ID);

            assertThat(res.getItemsDescription()).isEqualTo("Ivory Gown and 1 more");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when order does not exist")
        void orderNotFound() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.initiatePayment(ORDER_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws UnauthorizedException when order belongs to a different user")
        void orderBelongsToDifferentUser() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentService.initiatePayment(ORDER_ID, OTHER_ID))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("throws IllegalStateException when order status is not PENDING")
        void orderNotPending() {
            order.setStatus(OrderStatus.CONFIRMED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentService.initiatePayment(ORDER_ID, USER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CONFIRMED");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  handleWebhook
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("handleWebhook()")
    class HandleWebhook {

        private Map<String, String> validParams() {
            Map<String, String> p = new HashMap<>();
            p.put("merchant_id",       "test_merchant");
            p.put("order_id",          ORDER_ID.toString());
            p.put("payhere_amount",    "15000.00");
            p.put("payhere_currency",  "LKR");
            p.put("status_code",       "2");
            p.put("md5sig",            "MATCHING_HASH");
            p.put("payment_id",        "PAY-XYZ-001");
            return p;
        }

        @Test
        @DisplayName("marks payment COMPLETED, order CONFIRMED, and generates receipt on success")
        void successfulWebhook() {
            when(payHereUtil.generateHash("test_merchant", ORDER_ID.toString(),
                    "15000.00", "LKR")).thenReturn("MATCHING_HASH");
            when(paymentRepository.findByPayhereOrderId(ORDER_ID.toString()))
                    .thenReturn(Optional.of(pendingPayment));
            when(paymentRepository.save(any())).thenReturn(pendingPayment);
            when(orderRepository.save(any())).thenReturn(order);
            when(receiptService.generateReceipt(any(), any())).thenReturn(mock(Receipt.class));

            paymentService.handleWebhook(validParams());

            assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(pendingPayment.getPayherePaymentId()).isEqualTo("PAY-XYZ-001");
            assertThat(pendingPayment.getPaidAt()).isNotNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(receiptService).generateReceipt(order, pendingPayment);
        }

        @Test
        @DisplayName("does nothing when hash does not match — never updates payment")
        void hashMismatch() {
            when(payHereUtil.generateHash(any(), any(), any(), any()))
                    .thenReturn("EXPECTED_HASH");      // different from "MATCHING_HASH"
            Map<String, String> params = validParams();
            params.put("md5sig", "WRONG_HASH");

            paymentService.handleWebhook(params);

            verifyNoInteractions(paymentRepository);
            verifyNoInteractions(orderRepository);
        }

        @Test
        @DisplayName("does nothing when status_code is not 2 (e.g. failed payment)")
        void nonSuccessStatus() {
            when(payHereUtil.generateHash(any(), any(), any(), any()))
                    .thenReturn("MATCHING_HASH");
            Map<String, String> params = validParams();
            params.put("status_code", "-1");   // PayHere: -1 = cancelled

            paymentService.handleWebhook(params);

            verify(paymentRepository, never()).findByPayhereOrderId(any());
        }

        @Test
        @DisplayName("logs warning and does not throw when payhereOrderId is unknown")
        void unknownOrderId() {
            when(payHereUtil.generateHash(any(), any(), any(), any()))
                    .thenReturn("MATCHING_HASH");
            when(paymentRepository.findByPayhereOrderId(any())).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() -> paymentService.handleWebhook(validParams()));
        }

        @Test
        @DisplayName("hash comparison is case-insensitive")
        void hashCaseInsensitive() {
            when(payHereUtil.generateHash(any(), any(), any(), any()))
                    .thenReturn("abcdef123456");
            Map<String, String> params = validParams();
            params.put("md5sig", "ABCDEF123456");    // uppercase version
            when(paymentRepository.findByPayhereOrderId(any()))
                    .thenReturn(Optional.of(pendingPayment));
            when(paymentRepository.save(any())).thenReturn(pendingPayment);
            when(orderRepository.save(any())).thenReturn(order);
            when(receiptService.generateReceipt(any(), any())).thenReturn(mock(Receipt.class));

            assertThatNoException().isThrownBy(() -> paymentService.handleWebhook(params));
            assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  getPaymentStatus
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getPaymentStatus()")
    class GetPaymentStatus {

        @Test
        @DisplayName("returns COMPLETED status when a completed payment exists")
        void returnsExistingPaymentStatus() {
            pendingPayment.setStatus(PaymentStatus.COMPLETED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrder_Id(ORDER_ID))
                    .thenReturn(Optional.of(pendingPayment));

            PaymentStatusResponse res = paymentService.getPaymentStatus(ORDER_ID, USER_ID);

            assertThat(res.getStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("returns PENDING when no payment record exists yet")
        void returnsPendingWhenNoRecord() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrder_Id(ORDER_ID)).thenReturn(Optional.empty());

            PaymentStatusResponse res = paymentService.getPaymentStatus(ORDER_ID, USER_ID);

            assertThat(res.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when order does not exist")
        void orderNotFound() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentStatus(ORDER_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws UnauthorizedException when order belongs to a different user")
        void wrongUser() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentService.getPaymentStatus(ORDER_ID, OTHER_ID))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }
}