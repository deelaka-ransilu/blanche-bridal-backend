package edu.bridalshop.backend.service;

import edu.bridalshop.backend.dto.request.DamageItemRequest;
import edu.bridalshop.backend.dto.request.RentalCreateRequest;
import edu.bridalshop.backend.dto.request.RentalReturnRequest;
import edu.bridalshop.backend.dto.response.RentalResponse;
import edu.bridalshop.backend.entity.*;
import edu.bridalshop.backend.exception.ResourceNotFoundException;
import edu.bridalshop.backend.repository.*;
import edu.bridalshop.backend.util.PayloadSanitizer;
import edu.bridalshop.backend.util.PublicIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentalService {

    private final RentalRepository           rentalRepository;
    private final RentalDamageItemRepository damageItemRepository;
    private final DressRepository            dressRepository;
    private final DressFulfillmentOptionRepository fulfillmentRepository;
    private final UserRepository             userRepository;
    private final PublicIdGenerator          publicIdGenerator;
    private final PayloadSanitizer           sanitizer;

    // =========================================================================
    // LIST all rentals — newest first
    // =========================================================================

    @Transactional(readOnly = true)
    public List<RentalResponse> getAll() {
        return rentalRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(RentalResponse::from)
                .toList();
    }

    // =========================================================================
    // GET single rental
    // =========================================================================

    @Transactional(readOnly = true)
    public RentalResponse getByPublicId(String publicId) {
        return RentalResponse.from(findByPublicIdOrThrow(publicId));
    }

    // =========================================================================
    // LIST overdue rentals
    // =========================================================================

    @Transactional(readOnly = true)
    public List<RentalResponse> getOverdue() {
        return rentalRepository.findOverdue(LocalDate.now())
                .stream()
                .map(RentalResponse::from)
                .toList();
    }

    // =========================================================================
    // CREATE rental booking
    // Snapshots all price fields from dress_fulfillment_options at booking time
    // =========================================================================

    @Transactional
    public RentalResponse create(RentalCreateRequest request, String createdByEmail) {

        User createdBy = userRepository.findByEmail(createdByEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + createdByEmail));

        // Resolve dress
        Dress dress = dressRepository.findByPublicId(request.dressPublicId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dress not found: " + request.dressPublicId()));

        // Dress must be available
        if (!dress.getIsAvailable()) {
            throw new IllegalStateException(
                    "Dress " + request.dressPublicId() + " is not available for rental");
        }

        // Dress must have a RENTAL fulfillment option
        DressFulfillmentOption rentalOption = fulfillmentRepository
                .findByDress_DressIdAndFulfillmentType(dress.getDressId(), "RENTAL")
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dress " + request.dressPublicId() + " does not have a RENTAL option"));

        if (!rentalOption.getIsActive()) {
            throw new IllegalStateException("RENTAL option for this dress is not active");
        }

        // Resolve customer
        User customer = userRepository.findByPublicId(request.customerPublicId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found: " + request.customerPublicId()));

        if (!"CUSTOMER".equals(customer.getRole().name())) {
            throw new IllegalArgumentException(
                    "User " + request.customerPublicId() + " is not a CUSTOMER");
        }

        // ── Snapshot pricing from fulfillment option ─────────────────────────
        BigDecimal rentalPricePerDay = rentalOption.getRentalPricePerDay();
        Integer    rentalPeriodDays  = rentalOption.getRentalPeriodDays() != null
                ? rentalOption.getRentalPeriodDays() : 3;
        BigDecimal depositAmount     = rentalOption.getRentalDeposit();

        BigDecimal totalRentalFee    = rentalPricePerDay
                .multiply(BigDecimal.valueOf(rentalPeriodDays));
        BigDecimal totalPaidUpfront  = totalRentalFee.add(depositAmount);

        Rental rental = Rental.builder()
                .publicId(publicIdGenerator.forRental())
                .dress(dress)
                .customer(customer)
                .createdBy(createdBy)
                .nicNumber(sanitizer.sanitizeText(request.nicNumber()))
                .rentalPricePerDay(rentalPricePerDay)
                .rentalPeriodDays(rentalPeriodDays)
                .depositAmount(depositAmount)
                .totalRentalFee(totalRentalFee)
                .totalPaidUpfront(totalPaidUpfront)
                .status("BOOKED")
                .build();

        Rental saved = rentalRepository.save(rental);
        log.info("Rental created: {} for dress: {} customer: {}",
                saved.getPublicId(), request.dressPublicId(), request.customerPublicId());
        return RentalResponse.from(saved);
    }

    // =========================================================================
    // HANDOVER — BOOKED → HANDED_OVER
    // Marks dress unavailable, sets handedOverAt and dueDate
    // =========================================================================

    @Transactional
    public RentalResponse handover(String publicId) {
        Rental rental = findByPublicIdOrThrow(publicId);

        if (!"BOOKED".equals(rental.getStatus())) {
            throw new IllegalStateException(
                    "Only BOOKED rentals can be handed over. Current status: " + rental.getStatus());
        }

        LocalDateTime now    = LocalDateTime.now();
        LocalDate     due    = now.toLocalDate().plusDays(rental.getRentalPeriodDays());

        rental.setHandedOverAt(now);
        rental.setDueDate(due);
        rental.setStatus("HANDED_OVER");

        // Mark dress unavailable
        rental.getDress().setIsAvailable(false);
        dressRepository.save(rental.getDress());

        Rental saved = rentalRepository.save(rental);
        log.info("Rental handed over: {} due: {}", saved.getPublicId(), due);
        return RentalResponse.from(saved);
    }

    // =========================================================================
    // RETURN — Process return with optional damage items
    // Calculates all financial fields and determines final status
    // =========================================================================

    @Transactional
    public RentalResponse processReturn(String publicId, RentalReturnRequest request) {
        Rental rental = findByPublicIdOrThrow(publicId);

        if (!"HANDED_OVER".equals(rental.getStatus())
                && !"OVERDUE".equals(rental.getStatus())) {
            throw new IllegalStateException(
                    "Only HANDED_OVER or OVERDUE rentals can be returned. Current status: "
                            + rental.getStatus());
        }

        LocalDateTime returnedAt = LocalDateTime.now();
        LocalDate     returnDate = returnedAt.toLocalDate();
        LocalDate     dueDate    = rental.getDueDate();

        // ── Calculate days late ───────────────────────────────────────────────
        int daysLate = 0;
        if (returnDate.isAfter(dueDate)) {
            daysLate = (int) (returnDate.toEpochDay() - dueDate.toEpochDay());
        }

        BigDecimal lateFine = rental.getRentalPricePerDay()
                .multiply(BigDecimal.valueOf(daysLate));

        // ── Process damage items ──────────────────────────────────────────────
        BigDecimal totalDamageCost = BigDecimal.ZERO;
        boolean    hasDamage       = false;

        if (request.damageItems() != null && !request.damageItems().isEmpty()) {
            hasDamage = true;
            for (DamageItemRequest item : request.damageItems()) {
                RentalDamageItem damageItem = RentalDamageItem.builder()
                        .rental(rental)
                        .description(sanitizer.sanitizeText(item.description()))
                        .estimatedCost(item.estimatedCost())
                        .build();
                damageItemRepository.save(damageItem);
                rental.getDamageItems().add(damageItem);
                totalDamageCost = totalDamageCost.add(item.estimatedCost());
            }
        }

        // ── Financial calculations ────────────────────────────────────────────
        BigDecimal totalDeductions = lateFine.add(totalDamageCost);
        BigDecimal deposit         = rental.getDepositAmount();

        BigDecimal depositRefunded    = deposit.subtract(totalDeductions)
                .max(BigDecimal.ZERO);
        BigDecimal outstandingBalance = totalDeductions.subtract(deposit)
                .max(BigDecimal.ZERO);

        // ── Determine final status ────────────────────────────────────────────
        String finalStatus;
        if (outstandingBalance.compareTo(BigDecimal.ZERO) > 0) {
            finalStatus = "BALANCE_DUE";
        } else if (daysLate > 0 && hasDamage) {
            finalStatus = "RETURNED_LATE_DAMAGED";
        } else if (daysLate > 0) {
            finalStatus = "RETURNED_LATE";
        } else if (hasDamage) {
            finalStatus = "RETURNED_DAMAGED";
        } else {
            finalStatus = "RETURNED_CLEAN";
        }

        // ── Update rental record ──────────────────────────────────────────────
        rental.setReturnedAt(returnedAt);
        rental.setDaysLate(daysLate);
        rental.setLateFine(lateFine);
        rental.setTotalDamageCost(totalDamageCost);
        rental.setTotalDeductions(totalDeductions);
        rental.setDepositRefunded(depositRefunded);
        rental.setOutstandingBalance(outstandingBalance);
        rental.setStatus(finalStatus);
        rental.setReturnNotes(request.returnNotes() != null
                ? sanitizer.sanitizeText(request.returnNotes()) : null);

        // Mark dress available again
        rental.getDress().setIsAvailable(true);
        dressRepository.save(rental.getDress());

        Rental saved = rentalRepository.save(rental);
        log.info("Rental returned: {} status: {} daysLate: {} outstanding: {}",
                saved.getPublicId(), finalStatus, daysLate, outstandingBalance);
        return RentalResponse.from(saved);
    }

    // =========================================================================
    // MARK OVERDUE — called by scheduled job
    // Finds all HANDED_OVER rentals past due_date and flips to OVERDUE
    // =========================================================================

    @Transactional
    public int markOverdueRentals() {
        List<Rental> overdue = rentalRepository.findOverdue(LocalDate.now());
        for (Rental rental : overdue) {
            rental.setStatus("OVERDUE");
            rentalRepository.save(rental);
            log.warn("Rental marked OVERDUE: {} due: {}", rental.getPublicId(), rental.getDueDate());
        }
        return overdue.size();
    }

    // =========================================================================
    // Private helper
    // =========================================================================

    private Rental findByPublicIdOrThrow(String publicId) {
        return rentalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rental not found: " + publicId));
    }
}