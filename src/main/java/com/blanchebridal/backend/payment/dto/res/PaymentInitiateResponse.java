package com.blanchebridal.backend.payment.dto.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInitiateResponse {
    private String merchantId;
    private String orderId;
    private String amount;
    private String currency;
    private String hash;
    private String itemsDescription;
    private String customerFirstName;
    private String customerLastName;
    private String customerEmail;
    private String customerPhone;    // ← this is what's missing
    private String customerAddress;  // ← this
    private String customerCity;     // ← and this
    private String returnUrl;
    private String cancelUrl;
    private String notifyUrl;
}