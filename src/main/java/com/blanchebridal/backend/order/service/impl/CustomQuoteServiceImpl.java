package com.blanchebridal.backend.order.service.impl;

import com.blanchebridal.backend.appointment.entity.CustomDesignRequest;
import com.blanchebridal.backend.appointment.repository.CustomDesignRequestRepository;
import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.dto.req.CreateCustomQuoteRequest;
import com.blanchebridal.backend.order.dto.req.RejectQuoteRequest;
import com.blanchebridal.backend.order.dto.res.CustomQuoteResponse;
import com.blanchebridal.backend.order.entity.*;
import com.blanchebridal.backend.order.repository.CustomQuoteRepository;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.order.service.CustomQuoteService;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomQuoteServiceImpl implements CustomQuoteService {

    private static final BigDecimal INSTALLMENT_RATE = new BigDecimal("0.50");
    private static final int VALIDITY_DAYS = 2;

    private final CustomQuoteRepository customQuoteRepository;
    private final CustomDesignRequestRepository customDesignRequestRepository;
    private final OrderRepository orderRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public CustomQuoteResponse createQuote(UUID customDesignRequestId, CreateCustomQuoteRequest req, UUID adminId) {
        CustomDesignRequest designRequest = customDesignRequestRepository.findById(customDesignRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom design request not found: " + customDesignRequestId));

        if (req.otherAmount() != null && req.otherAmount().compareTo(BigDecimal.ZERO) > 0
                && (req.otherNote() == null || req.otherNote().isBlank())) {
            throw new IllegalArgumentException("otherNote is required when otherAmount is greater than zero");
        }

        CustomQuote latest = customQuoteRepository.findLatestOrNull(customDesignRequestId);
        if (latest != null && latest.getStatus() == QuoteStatus.PENDING && !isExpired(latest)) {
            throw new ConflictException(
                    "A pending, non-expired quote already exists for this design request — reject it first or wait for it to expire");
        }

        int nextVersion = (latest != null) ? latest.getVersion() + 1 : 1;

        BigDecimal total = req.fabricAmount()
                .add(req.laborAmount())
                .add(req.embellishmentAmount())
                .add(req.alterationsAmount())
                .add(req.otherAmount())
                .setScale(2, RoundingMode.HALF_UP);

        CustomQuote quote = CustomQuote.builder()
                .customDesignRequest(designRequest)
                .version(nextVersion)
                .fabricAmount(req.fabricAmount())
                .laborAmount(req.laborAmount())
                .embellishmentAmount(req.embellishmentAmount())
                .alterationsAmount(req.alterationsAmount())
                .otherAmount(req.otherAmount())
                .otherNote(req.otherNote())
                .totalAmount(total)
                .splitType(req.splitType())
                .status(QuoteStatus.PENDING)
                .validUntil(LocalDateTime.now().plusDays(VALIDITY_DAYS))
                .build();

        CustomQuote saved = customQuoteRepository.save(quote);

        log.info("[CustomQuote] Admin {} created quote v{} for design request {} — total LKR {}, split {}",
                adminId, saved.getVersion(), customDesignRequestId, total, req.splitType());

        sendQuoteEmailSafely(designRequest, saved);

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomQuoteResponse getLatestQuote(UUID customDesignRequestId, UUID requesterId, String role) {
        CustomDesignRequest designRequest = getOwnedOrStaffChecked(customDesignRequestId, requesterId, role);

        CustomQuote latest = customQuoteRepository.findLatestOrNull(designRequest.getId());
        if (latest == null) {
            throw new ResourceNotFoundException("No quote exists yet for this design request");
        }
        return toResponse(latest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomQuoteResponse> getQuoteHistory(UUID customDesignRequestId, UUID requesterId, String role) {
        getOwnedOrStaffChecked(customDesignRequestId, requesterId, role);

        return customQuoteRepository.findByCustomDesignRequest_IdOrderByVersionDesc(customDesignRequestId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CustomQuoteResponse approveQuote(UUID quoteId, UUID customerId) {
        CustomQuote quote = customQuoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found: " + quoteId));

        CustomDesignRequest designRequest = quote.getCustomDesignRequest();
        User owner = designRequest.getAppointment().getUser();
        if (owner == null || !owner.getId().equals(customerId)) {
            throw new UnauthorizedException("You do not own this custom design request");
        }

        if (quote.getStatus() != QuoteStatus.PENDING) {
            throw new ConflictException("Only a PENDING quote can be approved — current status: " + quote.getStatus());
        }
        if (isExpired(quote)) {
            throw new ConflictException("This quote has expired — ask for a new quote to be issued");
        }

        quote.setStatus(QuoteStatus.APPROVED);
        customQuoteRepository.save(quote);

        BigDecimal orderAmount = quote.getSplitType() == SplitType.FULL_UPFRONT
                ? quote.getTotalAmount()
                : quote.getTotalAmount().multiply(INSTALLMENT_RATE).setScale(2, RoundingMode.HALF_UP);

        Order order = Order.builder()
                .user(owner)
                .status(OrderStatus.PENDING)
                .totalAmount(orderAmount)
                .notes("Custom order — " + (quote.getSplitType() == SplitType.FULL_UPFRONT
                        ? "full payment"
                        : "50% deposit") + " (quote v" + quote.getVersion() + ")")
                .isCustomOrder(true)
                .build();

        Order savedOrder = orderRepository.save(order);

        designRequest.setFirstPaymentOrder(savedOrder);
        customDesignRequestRepository.save(designRequest);

        log.info("[CustomQuote] Customer {} approved quote {} (v{}) for design request {} — "
                        + "first-payment order {} created, amount LKR {}",
                customerId, quoteId, quote.getVersion(), designRequest.getId(), savedOrder.getId(), orderAmount);

        return toResponse(quote);
    }

    @Override
    @Transactional
    public CustomQuoteResponse rejectQuote(UUID quoteId, UUID customerId, RejectQuoteRequest req) {
        CustomQuote quote = customQuoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found: " + quoteId));

        CustomDesignRequest designRequest = quote.getCustomDesignRequest();
        User owner = designRequest.getAppointment().getUser();
        if (owner == null || !owner.getId().equals(customerId)) {
            throw new UnauthorizedException("You do not own this custom design request");
        }

        if (quote.getStatus() != QuoteStatus.PENDING) {
            throw new ConflictException("Only a PENDING quote can be rejected — current status: " + quote.getStatus());
        }

        quote.setStatus(QuoteStatus.REJECTED);
        quote.setRejectionReason(req.reason());
        CustomQuote saved = customQuoteRepository.save(quote);

        log.info("[CustomQuote] Customer {} rejected quote {} (v{}) for design request {} — reason: {}",
                customerId, quoteId, quote.getVersion(), designRequest.getId(), req.reason());

        return toResponse(saved);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private boolean isExpired(CustomQuote quote) {
        return quote.getValidUntil().isBefore(LocalDateTime.now());
    }

    private CustomDesignRequest getOwnedOrStaffChecked(UUID customDesignRequestId, UUID requesterId, String role) {
        CustomDesignRequest designRequest = customDesignRequestRepository.findById(customDesignRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom design request not found: " + customDesignRequestId));

        boolean isStaff = role != null && (role.contains("ADMIN") || role.contains("EMPLOYEE"));
        if (!isStaff) {
            User owner = designRequest.getAppointment().getUser();
            if (owner == null || !owner.getId().equals(requesterId)) {
                throw new UnauthorizedException("Access denied to this custom design request");
            }
        }
        return designRequest;
    }

    private void sendQuoteEmailSafely(CustomDesignRequest designRequest, CustomQuote quote) {
        try {
            User customer = designRequest.getAppointment().getUser();
            if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
                log.warn("[CustomQuote] Design request {} has no user/email — skipping quote email.", designRequest.getId());
                return;
            }
            String customerName = (customer.getFirstName() + " " + customer.getLastName()).trim();
            emailService.sendCustomQuoteEmail(
                    customer.getEmail(),
                    customerName.isEmpty() ? "Customer" : customerName,
                    designRequest.getId(),
                    toResponse(quote)
            );
        } catch (Exception e) {
            log.error("[CustomQuote] Failed to send quote email for design request {}: {}",
                    designRequest.getId(), e.getMessage(), e);
        }
    }

    private CustomQuoteResponse toResponse(CustomQuote q) {
        return CustomQuoteResponse.builder()
                .id(q.getId())
                .customDesignRequestId(q.getCustomDesignRequest().getId())
                .version(q.getVersion())
                .fabricAmount(q.getFabricAmount())
                .laborAmount(q.getLaborAmount())
                .embellishmentAmount(q.getEmbellishmentAmount())
                .alterationsAmount(q.getAlterationsAmount())
                .otherAmount(q.getOtherAmount())
                .otherNote(q.getOtherNote())
                .totalAmount(q.getTotalAmount())
                .splitType(q.getSplitType())
                .status(q.getStatus())
                .rejectionReason(q.getRejectionReason())
                .validUntil(q.getValidUntil())
                .createdAt(q.getCreatedAt())
                .isExpired(isExpired(q))
                .build();
    }
}