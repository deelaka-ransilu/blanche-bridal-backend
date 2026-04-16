package com.blanchebridal.backend.payment.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PayHereUtil {

    @Value("${payhere.secret}")
    private String secret;

    /**
     * Generates the MD5 hash required by PayHere.
     * Formula: MD5( merchantId + orderId + amount + currency + MD5(secret).toUpperCase() ).toUpperCase()
     * PAYHERE_SECRET must NEVER leave the server — this method is server-side only.
     */
    public String generateHash(String merchantId, String orderId,
                               String amount, String currency) {
        String upperSecret = DigestUtils.md5Hex(secret).toUpperCase();
        String raw = merchantId + orderId + amount + currency + upperSecret;
        return DigestUtils.md5Hex(raw).toUpperCase();
    }
}