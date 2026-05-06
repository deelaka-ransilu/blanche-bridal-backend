package com.blanchebridal.backend.payment.util;

import jakarta.annotation.PostConstruct;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PayHereUtil {

    @Value("${payhere.secret}")
    private String secret;

    // For payment form initiation
    public String generateHash(String merchantId, String orderId,
                               String amount, String currency) {
        String upperSecret = DigestUtils.md5Hex(secret).toUpperCase(); // ← no decode
        String raw = merchantId + orderId + amount + currency + upperSecret;
        return DigestUtils.md5Hex(raw).toUpperCase();
    }

    // For webhook notification verification
    public String generateNotifyHash(String merchantId, String orderId,
                                     String amount, String currency,
                                     String statusCode) {
        String upperSecret = DigestUtils.md5Hex(secret).toUpperCase(); // ← no decode
        String raw = merchantId + orderId + amount + currency + statusCode + upperSecret;
        return DigestUtils.md5Hex(raw).toUpperCase();
    }
}