package com.blanchebridal.backend.payment.util;

import jakarta.annotation.PostConstruct;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class PayHereUtil {

    @Value("${payhere.secret}")
    private String secret;

    public String generateHash(String merchantId, String orderId,
                               String amount, String currency) {
        String decodedSecret = new String(Base64.getDecoder().decode(secret));
        String upperSecret = DigestUtils.md5Hex(decodedSecret).toUpperCase();
        String raw = merchantId + orderId + amount + currency + upperSecret;
        return DigestUtils.md5Hex(raw).toUpperCase();
    }
}