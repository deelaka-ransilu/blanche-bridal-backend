package com.blanchebridal.backend.order.dto.res;

import com.blanchebridal.backend.order.entity.DiscountType;
import com.blanchebridal.backend.order.entity.OrderMode;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.payment.entity.PaymentMethod;
import com.blanchebridal.backend.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrderResponse {

    private UUID id;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String notes;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String customerEmail;
    private String customerFirstName;
    private String customerLastName;
    private String customerPhone;
    private String fulfillmentMethod;
    private String deliveryAddress;
    private OrderMode orderMode;
    private PaymentMethod paymentMethod;
    private Boolean isRentalDeposit;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private String discountReason;

    private PaymentStatus paymentStatus;
    private BigDecimal refundAmount;
    private LocalDateTime refundedAt;

    // The admin-uploaded proof-of-transfer image (Cloudinary URL), surfaced
    // to the customer once a refund has been issued so "a copy of the
    // receipt is on file" is an actual clickable link, not just a claim.
    private String refundProofImageUrl;

    // True once the customer has submitted RefundBankDetails for this
    // order, regardless of whether the refund itself has been issued yet.
    private Boolean bankDetailsSubmitted;
}