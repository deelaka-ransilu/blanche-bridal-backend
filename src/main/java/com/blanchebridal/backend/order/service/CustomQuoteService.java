package com.blanchebridal.backend.order.service;

import com.blanchebridal.backend.order.dto.req.CreateCustomQuoteRequest;
import com.blanchebridal.backend.order.dto.req.RejectQuoteRequest;
import com.blanchebridal.backend.order.dto.res.CustomQuoteResponse;

import java.util.List;
import java.util.UUID;

public interface CustomQuoteService {

    CustomQuoteResponse createQuote(UUID customDesignRequestId, CreateCustomQuoteRequest req, UUID adminId);

    CustomQuoteResponse getLatestQuote(UUID customDesignRequestId, UUID requesterId, String role);

    List<CustomQuoteResponse> getQuoteHistory(UUID customDesignRequestId, UUID requesterId, String role);

    CustomQuoteResponse approveQuote(UUID quoteId, UUID customerId);

    CustomQuoteResponse rejectQuote(UUID quoteId, UUID customerId, RejectQuoteRequest req);
}