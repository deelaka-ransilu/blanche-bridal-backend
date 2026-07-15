package com.blanchebridal.backend.refund.service;

import com.blanchebridal.backend.refund.dto.RefundResponse;

import java.util.UUID;

public interface RefundService {
    RefundResponse createRefund(UUID orderId, String reason, UUID adminId);
}