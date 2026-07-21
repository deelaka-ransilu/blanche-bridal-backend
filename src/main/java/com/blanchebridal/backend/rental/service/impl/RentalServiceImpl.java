package com.blanchebridal.backend.rental.service.impl;

import com.blanchebridal.backend.appointment.entity.Appointment;
import com.blanchebridal.backend.appointment.entity.AppointmentStatus;
import com.blanchebridal.backend.appointment.entity.AppointmentType;
import com.blanchebridal.backend.appointment.repository.AppointmentRepository;
import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.order.dto.res.OrderItemResponse;
import com.blanchebridal.backend.order.dto.res.OrderResponse;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.payment.entity.PaymentMethod;
import com.blanchebridal.backend.product.entity.ProductType;
import com.blanchebridal.backend.rental.dto.req.CreateRentalBookingRequest;
import com.blanchebridal.backend.rental.dto.req.HandoverRequest;
import com.blanchebridal.backend.rental.dto.req.MarkReturnedRequest;
import com.blanchebridal.backend.rental.dto.res.RentableProductResponse;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderItem;
import com.blanchebridal.backend.order.entity.OrderMode;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.rental.dto.req.CreateRentalRequest;
import com.blanchebridal.backend.rental.dto.req.RentalBookingRequest;
import com.blanchebridal.backend.rental.dto.req.UpdateBalanceRequest;
import com.blanchebridal.backend.rental.dto.res.RentalResponse;
import com.blanchebridal.backend.rental.entity.Rental;
import com.blanchebridal.backend.rental.entity.RentalStatus;
import com.blanchebridal.backend.rental.repository.RentalRepository;
import com.blanchebridal.backend.rental.service.RentalService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentalServiceImpl implements RentalService {

    private static final List<RentalStatus> BOOKED_STATUSES =
            List.of(RentalStatus.PENDING_PAYMENT, RentalStatus.BOOKED, RentalStatus.ACTIVE);

    // Fitting must be booked at least this many days before rentalStart, so
    // the shop has time to do alterations after the fitting.
    private static final int FITTING_CUTOFF_DAYS_BEFORE_START = 2;

    // Security deposit = this fraction of the total rental fee.
    private static final BigDecimal SECURITY_DEPOSIT_RATE = new BigDecimal("0.30");

    // Each payment installment is this fraction of the total rental fee.
    private static final BigDecimal INSTALLMENT_RATE = new BigDecimal("0.50");

    // Flat late-return fee per day late. Capped at the security deposit
    // amount (see markReturned). Placeholder value — surface as a real
    // configurable/admin-settable constant once the business confirms the
    // exact LKR figure.
    private static final BigDecimal LATE_FEE_PER_DAY = new BigDecimal("1000.00");

    private final RentalRepository rentalRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final EmailService emailService;
    private final AppointmentRepository appointmentRepository;

    @Override
    @Transactional
    public RentalResponse createRental(CreateRentalRequest req) {
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        boolean alreadyRented = rentalRepository.existsByProduct_IdAndStatusIn(
                req.getProductId(), List.of(RentalStatus.ACTIVE, RentalStatus.OVERDUE));
        if (alreadyRented) {
            throw new IllegalStateException(
                    "Product is currently rented out and not yet returned");
        }

        Order order = null;
        if (req.getOrderId() != null) {
            order = orderRepository.findById(req.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        }

        Rental rental = Rental.builder()
                .user(user)
                .product(product)
                .order(order)
                .rentalStart(req.getRentalStart())
                .rentalEnd(req.getRentalEnd())
                .depositAmount(req.getDepositAmount())
                .notes(req.getNotes())
                .status(RentalStatus.ACTIVE)
                .build();

        return toResponse(rentalRepository.save(rental));
    }

    @Override
    @Transactional
    public RentalResponse bookRental(RentalBookingRequest req, UUID callerId) {
        User user = userRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (Boolean.FALSE.equals(product.getIsActive()) || Boolean.FALSE.equals(product.getIsAvailable())) {
            throw new IllegalStateException("Product is not available: " + product.getName());
        }

        if (product.getRentalPrice() == null && product.getRentalPricePerDay() == null) {
            throw new IllegalStateException("Product is not available for rental: " + product.getName());
        }

        if (!req.getRentalEnd().equals(req.getRentalStart().plusDays(1))) {
            throw new IllegalArgumentException("Rental end date must be exactly one day after the start date");
        }

        if (req.getRentalStart().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Rental start date cannot be in the past");
        }

        // Fitting must be on/before rentalStart - 2 days. Validated
        // server-side regardless of what the frontend computed and sent —
        // the frontend cutoff display is UX only.
        LocalDate fittingCutoff = req.getRentalStart().minusDays(FITTING_CUTOFF_DAYS_BEFORE_START);
        if (req.getFittingDate().isAfter(fittingCutoff)) {
            throw new IllegalArgumentException(
                    "Fitting must be booked on or before " + fittingCutoff
                            + " (2 days before the rental start date)");
        }
        if (req.getFittingDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Fitting date cannot be in the past");
        }

        boolean alreadyRented = rentalRepository.existsByProduct_IdAndStatusIn(
                req.getProductId(), List.of(RentalStatus.ACTIVE, RentalStatus.OVERDUE));
        if (alreadyRented) {
            throw new IllegalStateException(
                    "Product is currently rented out and not yet returned");
        }

        // Per-day pricing takes priority when set on the product; otherwise
        // fall back to the flat one-time rentalPrice fee.
        BigDecimal rentalFee;
        long days = ChronoUnit.DAYS.between(req.getRentalStart(), req.getRentalEnd());
        if (product.getRentalPricePerDay() != null) {
            rentalFee = product.getRentalPricePerDay().multiply(BigDecimal.valueOf(days));
        } else {
            rentalFee = product.getRentalPrice();
        }
        rentalFee = rentalFee.setScale(2, RoundingMode.HALF_UP);

        BigDecimal securityDeposit = rentalFee.multiply(SECURITY_DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal firstInstallment = rentalFee.multiply(INSTALLMENT_RATE).setScale(2, RoundingMode.HALF_UP);

        // Fitting slot — same slot pool as the standalone appointment
        // booking flow, but keyed on fittingDate, not rentalStart.
        boolean slotTaken = appointmentRepository.existsByAppointmentDateAndTimeSlotAndStatusNot(
                req.getFittingDate(), req.getFittingTimeSlot(), AppointmentStatus.CANCELLED);
        if (slotTaken) {
            throw new IllegalStateException(
                    "Fitting slot " + req.getFittingTimeSlot() + " on " + req.getFittingDate()
                            + " is no longer available");
        }

        Appointment fittingAppointment = Appointment.builder()
                .user(user)
                .product(product)
                .appointmentDate(req.getFittingDate())
                .timeSlot(req.getFittingTimeSlot())
                .type(AppointmentType.RENTAL_FITTING)
                .notes("Auto-created for rental booking")
                .build();
        Appointment savedAppointment = appointmentRepository.save(fittingAppointment);

        String imageUrl = (product.getImages() != null && !product.getImages().isEmpty())
                ? product.getImages().get(0).getUrl()
                : null;

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(1)
                .unitPrice(firstInstallment)
                .productName(product.getName() + " — rental (50% deposit)")
                .productImage(imageUrl)
                .size(req.getSize())
                .build();

        Order syntheticOrder = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .totalAmount(firstInstallment)
                .notes("Rental fitting-booking payment (50% of rental fee) — auto-generated")
                .orderMode(OrderMode.WEBSITE)
                .paymentMethod(req.getPaymentMethod())
                .isRentalDeposit(true)
                .items(List.of(item))
                .build();

        item.setOrder(syntheticOrder);
        Order savedOrder = orderRepository.save(syntheticOrder);

        Rental rental = Rental.builder()
                .user(user)
                .product(product)
                .order(savedOrder)
                .appointment(savedAppointment)
                .rentalStart(req.getRentalStart())
                .rentalEnd(req.getRentalEnd())
                .rentalFee(rentalFee)
                .securityDepositAmount(securityDeposit)
                .status(RentalStatus.PENDING_PAYMENT)
                .build();

        Rental savedRental = rentalRepository.save(rental);

        log.info("[Rental] Customer {} booked rental for product {} — synthetic order {} created, {} days, "
                        + "rental fee LKR {} (50% due now: LKR {}, security deposit LKR {} due at handover), "
                        + "fitting slot {} on {}",
                callerId, req.getProductId(), savedOrder.getId(), days, rentalFee, firstInstallment,
                securityDeposit, req.getFittingTimeSlot(), req.getFittingDate());

        return toResponse(savedRental);
    }

    @Override
    @Transactional
    public RentalResponse confirmHandover(UUID id, HandoverRequest req, UUID callerId, String role) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found: " + id));

        boolean isStaff = role != null && role.contains("ADMIN");
        if (!isStaff) {
            throw new UnauthorizedException("Only staff can confirm a rental handover");
        }

        if (rental.getStatus() != RentalStatus.BOOKED) {
            throw new ConflictException(
                    "Rental must be BOOKED (fitting paid) before handover can be confirmed — current status: "
                            + rental.getStatus());
        }

        if (rental.getHandoverOrder() != null) {
            throw new ConflictException("Handover payment has already been created for this rental");
        }

        BigDecimal remainingInstallment = rental.getRentalFee()
                .multiply(INSTALLMENT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal handoverTotal = remainingInstallment
                .add(rental.getSecurityDepositAmount())
                .setScale(2, RoundingMode.HALF_UP);

        Product product = rental.getProduct();
        String imageUrl = (product != null && product.getImages() != null && !product.getImages().isEmpty())
                ? product.getImages().get(0).getUrl()
                : null;

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(1)
                .unitPrice(handoverTotal)
                .productName((product != null ? product.getName() : "Rental")
                        + " — handover (remaining 50% + security deposit)")
                .productImage(imageUrl)
                .build();

        Order handoverOrder = Order.builder()
                .user(rental.getUser())
                .status(OrderStatus.PENDING)
                .totalAmount(handoverTotal)
                .notes("Rental handover payment (remaining rental fee + security deposit) — auto-generated")
                .orderMode(OrderMode.WALK_IN)
                .paymentMethod(req.getPaymentMethod())
                .isRentalDeposit(true)
                .items(List.of(item))
                .build();
        item.setOrder(handoverOrder);

        Order savedHandoverOrder = orderRepository.save(handoverOrder);

        rental.setHandoverOrder(savedHandoverOrder);
        Rental savedRental = rentalRepository.save(rental);

        log.info("[Rental] Handover payment created for rental {} — order {}, total LKR {} "
                        + "(remaining fee LKR {} + security deposit LKR {}), method {}, confirmed by {}",
                id, savedHandoverOrder.getId(), handoverTotal, remainingInstallment,
                rental.getSecurityDepositAmount(), req.getPaymentMethod(), callerId);

        return toResponse(savedRental);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RentalResponse> getAllRentals(RentalStatus status, Pageable pageable) {
        Page<Rental> page = status != null
                ? rentalRepository.findByStatus(status, pageable)
                : rentalRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RentalResponse> getMyRentals(UUID userId) {
        return rentalRepository.findByUser_Id(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RentalResponse getRentalById(UUID id, UUID requestingUserId, String role) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found: " + id));

        boolean isCustomer = role != null &&
                (role.equals("ROLE_CUSTOMER") || role.equals("CUSTOMER"));

        if (isCustomer) {
            if (rental.getUser() == null ||
                    !rental.getUser().getId().equals(requestingUserId)) {
                throw new UnauthorizedException("Access denied to this rental");
            }
        }

        return toResponse(rental);
    }

    @Override
    @Transactional
    public RentalResponse markReturned(UUID id, MarkReturnedRequest req) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found: " + id));

        rental.setStatus(RentalStatus.RETURNED);
        rental.setReturnDate(req.getReturnDate());

        BigDecimal deposit = rental.getSecurityDepositAmount() != null
                ? rental.getSecurityDepositAmount() : BigDecimal.ZERO;

        BigDecimal damageCost = (req.isDamaged() && req.getDamageCost() != null)
                ? req.getDamageCost().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long daysLate = Math.max(0, ChronoUnit.DAYS.between(rental.getRentalEnd(), req.getReturnDate()));
        BigDecimal lateFee = BigDecimal.ZERO;
        if (daysLate > 0) {
            BigDecimal rawLateFee = LATE_FEE_PER_DAY.multiply(BigDecimal.valueOf(daysLate));
            // Capped at the security deposit amount.
            lateFee = rawLateFee.min(deposit).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalDeduction = damageCost.add(lateFee);

        BigDecimal refundedAmount;
        BigDecimal owedAmount;
        if (totalDeduction.compareTo(deposit) > 0) {
            refundedAmount = BigDecimal.ZERO;
            owedAmount = totalDeduction.subtract(deposit).setScale(2, RoundingMode.HALF_UP);
        } else {
            refundedAmount = deposit.subtract(totalDeduction).setScale(2, RoundingMode.HALF_UP);
            owedAmount = BigDecimal.ZERO;
        }

        rental.setDamageCost(damageCost);
        rental.setLateFeeAmount(lateFee);
        rental.setSecurityDepositRefundedAmount(refundedAmount);
        rental.setAmountOwedByCustomer(owedAmount);

        log.info("[Rental] Rental {} returned on {} ({} day(s) late) — damage LKR {}, late fee LKR {}, "
                        + "deposit refunded LKR {}, amount owed by customer LKR {}",
                id, req.getReturnDate(), daysLate, damageCost, lateFee, refundedAmount, owedAmount);

        return toResponse(rentalRepository.save(rental));
    }

    @Override
    @Transactional
    public RentalResponse updateBalance(UUID id, UpdateBalanceRequest req) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found: " + id));

        rental.setBalanceDue(req.getBalanceDue());
        return toResponse(rentalRepository.save(rental));
    }

    @Override
    @Transactional
    public RentalResponse cancelRental(UUID id, UUID userId, String role) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found: " + id));

        boolean isOwner = rental.getUser() != null && rental.getUser().getId().equals(userId);
        boolean isStaff = "ROLE_ADMIN".equals(role);
        if (!isOwner && !isStaff) {
            throw new UnauthorizedException("Not authorized to cancel this rental");
        }

        // Cancellable any time before handover — both the pre-fitting-payment
        // case (PENDING_PAYMENT) and the post-fitting/pre-pickup case (BOOKED,
        // full rental fee refunded per policy).
        if (rental.getStatus() != RentalStatus.PENDING_PAYMENT && rental.getStatus() != RentalStatus.BOOKED) {
            throw new ConflictException("This rental can no longer be cancelled");
        }

        // Cancel the fitting appointment itself so its slot frees up.
        if (rental.getAppointment() != null
                && rental.getAppointment().getStatus() != AppointmentStatus.CANCELLED) {
            rental.getAppointment().setStatus(AppointmentStatus.CANCELLED);
            appointmentRepository.save(rental.getAppointment());
        }

        Order order = rental.getOrder();
        if (order != null && order.getStatus() == OrderStatus.PENDING) {
            // Payment for the fitting installment never completed — just
            // cancel it, nothing to refund.
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
        }
        // NOTE: if the fitting payment already completed (rental.status ==
        // BOOKED), the order stays CONFIRMED here. Per policy the full rental
        // fee is refundable in that case — the actual refund (with proof of
        // bank transfer / cash handback) is processed by an admin through the
        // existing refund flow (RefundController / RefundOrderButton) against
        // rental.getOrder().getId(), same as any other order refund. This
        // keeps refund processing consistent everywhere rather than adding a
        // second, auto-triggered refund path here.

        rental.setStatus(RentalStatus.CANCELLED);
        return toResponse(rentalRepository.save(rental));
    }

    @Override
    @Transactional
    public void markOverdueRentals() {
        List<Rental> overdueRentals = rentalRepository
                .findByStatusAndRentalEndBefore(RentalStatus.ACTIVE, LocalDate.now());

        if (overdueRentals.isEmpty()) {
            log.info("[RentalScheduler] No overdue rentals found.");
            return;
        }

        for (Rental rental : overdueRentals) {
            rental.setStatus(RentalStatus.OVERDUE);
            rentalRepository.save(rental);

            try {
                User customer = rental.getUser();
                Product product = rental.getProduct();
                if (customer != null && product != null) {
                    emailService.sendRentalOverdueEmail(
                            customer.getEmail(),
                            customer.getFirstName() + " " + customer.getLastName(),
                            product.getName(),
                            rental.getRentalEnd(),
                            rental.getBalanceDue()
                    );
                }
            } catch (Exception e) {
                log.warn("Failed to send overdue email for rental {}: {}",
                        rental.getId(), e.getMessage());
            }
        }

        log.info("[RentalScheduler] Marked {} rental(s) as OVERDUE.", overdueRentals.size());
    }

    @Override
    @Transactional
    public void markActiveRentals() {
        // Fallback safety net only now — the normal path to ACTIVE is
        // PaymentServiceImpl.handleRentalHandoverConfirmed() firing the
        // moment the handover payment completes. This just catches any
        // BOOKED rental whose start date has arrived without an explicit
        // handover confirmation being recorded (e.g. manual data fixes).
        List<Rental> toActivate = rentalRepository
                .findByStatusAndRentalStartLessThanEqual(RentalStatus.BOOKED, LocalDate.now());

        if (toActivate.isEmpty()) {
            log.info("[RentalScheduler] No rentals to activate.");
            return;
        }

        for (Rental rental : toActivate) {
            rental.setStatus(RentalStatus.ACTIVE);
            rentalRepository.save(rental);
            log.info("[RentalScheduler] Rental {} transitioned BOOKED -> ACTIVE (start date {} reached, "
                    + "no handover payment recorded).", rental.getId(), rental.getRentalStart());
        }

        log.info("[RentalScheduler] Activated {} rental(s).", toActivate.size());
    }

    @Override
    @Transactional
    public void expireStaleBookings() {
        LocalDate cutoff = LocalDate.now().minusDays(2);

        List<Rental> toExpire = rentalRepository
                .findByStatusAndRentalStartLessThanEqual(RentalStatus.PENDING_PAYMENT, cutoff);

        if (toExpire.isEmpty()) {
            log.info("[RentalScheduler] No stale PENDING_PAYMENT bookings to expire.");
            return;
        }

        for (Rental rental : toExpire) {
            rental.setStatus(RentalStatus.CANCELLED);

            if (rental.getAppointment() != null
                    && rental.getAppointment().getStatus() != AppointmentStatus.CANCELLED) {
                rental.getAppointment().setStatus(AppointmentStatus.CANCELLED);
                appointmentRepository.save(rental.getAppointment());
            }

            rentalRepository.save(rental);

            Order order = rental.getOrder();
            if (order != null && order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
            }

            log.info("[RentalScheduler] Rental {} expired — fitting-booking payment (50%) not received within "
                            + "the grace period. Alterations were not started; booking and fitting cancelled.",
                    rental.getId());
        }

        log.info("[RentalScheduler] Expired {} stale rental booking(s).", toExpire.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RentableProductResponse> getRentableProducts() {
        return productRepository.findAll().stream()
                .filter(p -> p.getType() == ProductType.DRESS)
                .filter(p -> Boolean.TRUE.equals(p.getIsAvailable()))
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> !rentalRepository.existsByProduct_IdAndStatusIn(p.getId(), BOOKED_STATUSES))
                .map(this::toRentableResponse)
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse createRentalBooking(CreateRentalBookingRequest req, UUID callerId, String role) {

        boolean isStaff = role != null && (role.equals("ROLE_ADMIN") || role.equals("ADMIN"));

        if (!isStaff) {
            throw new IllegalStateException("Rental bookings can only be created by staff");
        }

        User customer = userRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new IllegalStateException("Rentals can only be booked for CUSTOMER accounts");
        }

        Product product = productRepository.findByIdForUpdate(req.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + req.getProductId()));

        if (product.getType() != ProductType.DRESS) {
            throw new IllegalStateException("Only dresses can be rented: " + product.getName());
        }
        if (Boolean.FALSE.equals(product.getIsActive()) || Boolean.FALSE.equals(product.getIsAvailable())) {
            throw new IllegalStateException("Product is not available: " + product.getName());
        }
        if (rentalRepository.existsByProduct_IdAndStatusIn(product.getId(), BOOKED_STATUSES)) {
            throw new IllegalStateException("This gown is already booked: " + product.getName());
        }

        BigDecimal fee;
        if (product.getRentalPricePerDay() != null) {
            long days = ChronoUnit.DAYS.between(req.getRentalStart(), req.getRentalEnd());
            fee = product.getRentalPricePerDay().multiply(BigDecimal.valueOf(days));
        } else {
            fee = product.getRentalPrice() != null ? product.getRentalPrice() : BigDecimal.ZERO;
        }

        String imageUrl = (product.getImages() != null && !product.getImages().isEmpty())
                ? product.getImages().get(0).getUrl()
                : null;

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(1)
                .unitPrice(fee)
                .productName(product.getName())
                .productImage(imageUrl)
                .build();

        Order order = Order.builder()
                .user(customer)
                .status(OrderStatus.PENDING)
                .totalAmount(fee)
                .notes(req.getNotes())
                .fulfillmentMethod("PICKUP")
                .customerPhone(customer.getPhone())
                .orderMode(OrderMode.WALK_IN)
                .paymentMethod(req.getPaymentMethod())
                .isRentalDeposit(true)
                .items(List.of(item))
                .build();
        item.setOrder(order);

        Order savedOrder = orderRepository.save(order);

        Rental rental = Rental.builder()
                .user(customer)
                .product(product)
                .order(savedOrder)
                .rentalStart(req.getRentalStart())
                .rentalEnd(req.getRentalEnd())
                .status(RentalStatus.PENDING_PAYMENT)
                .rentalFee(fee)
                .depositAmount(fee)
                .balanceDue(BigDecimal.ZERO)
                .notes(req.getNotes())
                .build();
        rentalRepository.save(rental);

        log.info("[Rental] Created walk-in rental booking — order {} / product {} for customer {} "
                        + "(created by {}) — fee LKR {}",
                savedOrder.getId(), product.getId(), customer.getId(), callerId, fee);

        return toResponse(savedOrder);
    }

    // ─── Mapper ───────────────────────────────────────────────────────────────

    private RentalResponse toResponse(Rental rental) {
        String productName = rental.getProduct() != null ? rental.getProduct().getName() : null;
        String productImage = null;
        if (rental.getProduct() != null &&
                rental.getProduct().getImages() != null &&
                !rental.getProduct().getImages().isEmpty()) {
            productImage = rental.getProduct().getImages().get(0).getUrl();
        }

        String customerName = null;
        String customerEmail = null;
        if (rental.getUser() != null) {
            customerName = rental.getUser().getFirstName() + " " + rental.getUser().getLastName();
            customerEmail = rental.getUser().getEmail();
        }

        return RentalResponse.builder()
                .id(rental.getId())
                .productId(rental.getProduct() != null ? rental.getProduct().getId() : null)
                .productName(productName)
                .productImage(productImage)
                .userId(rental.getUser() != null ? rental.getUser().getId() : null)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .orderId(rental.getOrder() != null ? rental.getOrder().getId() : null)
                .paymentMethod(rental.getOrder() != null ? rental.getOrder().getPaymentMethod() : null)
                .handoverOrderId(rental.getHandoverOrder() != null ? rental.getHandoverOrder().getId() : null)
                .rentalStart(rental.getRentalStart())
                .rentalEnd(rental.getRentalEnd())
                .returnDate(rental.getReturnDate())
                .status(rental.getStatus())
                .rentalFee(rental.getRentalFee())
                .securityDepositAmount(rental.getSecurityDepositAmount())
                .securityDepositRefundedAmount(rental.getSecurityDepositRefundedAmount())
                .damageCost(rental.getDamageCost())
                .lateFeeAmount(rental.getLateFeeAmount())
                .amountOwedByCustomer(rental.getAmountOwedByCustomer())
                .handoverConfirmedAt(rental.getHandoverConfirmedAt())
                .depositAmount(rental.getDepositAmount())
                .balanceDue(rental.getBalanceDue())
                .notes(rental.getNotes())
                .createdAt(rental.getCreatedAt())
                .fittingDate(rental.getAppointment() != null ? rental.getAppointment().getAppointmentDate() : null)
                .fittingTimeSlot(rental.getAppointment() != null ? rental.getAppointment().getTimeSlot() : null)
                .fittingAppointmentId(rental.getAppointment() != null ? rental.getAppointment().getId() : null)
                .build();
    }

    private RentableProductResponse toRentableResponse(Product p) {
        return RentableProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .type(p.getType())
                .rentalPrice(p.getRentalPrice())
                .rentalPricePerDay(p.getRentalPricePerDay())
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .firstImageUrl((p.getImages() != null && !p.getImages().isEmpty())
                        ? p.getImages().get(0).getUrl()
                        : null)
                .build();
    }

    // Mirrors OrderServiceImpl.toResponse/toItemResponse exactly, duplicated
    // here rather than made public on OrderService.
    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems() == null
                ? List.of()
                : order.getItems().stream().map(this::toItemResponse).toList();

        String email = order.getUser() != null ? order.getUser().getEmail() : null;
        String firstName = order.getUser() != null ? order.getUser().getFirstName() : null;
        String lastName = order.getUser() != null ? order.getUser().getLastName() : null;

        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .notes(order.getNotes())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .customerEmail(email)
                .customerFirstName(firstName)
                .customerLastName(lastName)
                .fulfillmentMethod(order.getFulfillmentMethod())
                .deliveryAddress(order.getDeliveryAddress())
                .customerPhone(order.getCustomerPhone())
                .orderMode(order.getOrderMode())
                .paymentMethod(order.getPaymentMethod())
                .isRentalDeposit(order.getIsRentalDeposit())
                .discountType(order.getDiscountType())
                .discountValue(order.getDiscountValue())
                .discountReason(order.getDiscountReason())
                .build();
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return OrderItemResponse.builder()
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProductName())
                .productImage(item.getProductImage())
                .productType(item.getProduct() != null ? item.getProduct().getType() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .size(item.getSize())
                .subtotal(subtotal)
                .build();
    }
}