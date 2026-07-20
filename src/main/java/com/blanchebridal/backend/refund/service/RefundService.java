package com.blanchebridal.backend.refund.service;

import com.blanchebridal.backend.refund.dto.BankDetailsResponse;
import com.blanchebridal.backend.refund.dto.RefundResponse;
import com.blanchebridal.backend.refund.dto.SubmitBankDetailsRequest;

import java.util.UUID;

public interface RefundService {
    RefundResponse createRefund(UUID orderId, String reason, String proofImageUrl, UUID adminId);
    BankDetailsResponse submitBankDetails(UUID orderId, UUID customerId, SubmitBankDetailsRequest request);
    BankDetailsResponse getBankDetails(UUID orderId);
}