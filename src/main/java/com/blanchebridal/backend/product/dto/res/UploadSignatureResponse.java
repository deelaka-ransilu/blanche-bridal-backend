package com.blanchebridal.backend.product.dto.res;

public record UploadSignatureResponse(
        String signature,
        long timestamp,
        String apiKey,
        String cloudName,
        String folder
) {}