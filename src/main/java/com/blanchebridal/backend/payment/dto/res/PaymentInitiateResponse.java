package com.blanchebridal.backend.payment.dto.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInitiateResponse {
    private String merchantId;
    private String orderId;
    private String amount;       // formatted as "0.00"
    private String currency;     // always "LKR"
    private String hash;
    private String itemsDescription;
    private String customerFirstName;
    private String customerLastName;
    private String customerEmail;
    private String returnUrl;
    private String cancelUrl;
    private String notifyUrl;
}