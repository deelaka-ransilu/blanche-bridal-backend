package com.blanchebridal.backend.rental.service.impl;

import com.blanchebridal.backend.exception.ConflictException;
import com.blanchebridal.backend.order.dto.res.OrderItemResponse;
import com.blanchebridal.backend.order.dto.res.OrderResponse;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.payment.entity.PaymentMethod;
import com.blanchebridal.backend.product.entity.ProductType;
import com.blanchebridal.backend.rental.dto.req.CreateRentalBookingRequest;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentalServiceImpl implements RentalService {


    private static final List<RentalStatus> BOOKED_STATUSES =
            List.of(RentalStatus.PENDING_PAYMENT, RentalStatus.BOOKED, RentalStatus.ACTIVE);


    private final RentalRepository rentalRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final EmailService emailService;



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

        if (!req.getRentalEnd().isAfter(req.getRentalStart())) {
            throw new IllegalArgumentException("Rental end date must be after start date");
        }

        if (req.getRentalStart().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Rental start date cannot be in the past");
        }

        boolean alreadyRented = rentalRepository.existsByProduct_IdAndStatusIn(
                req.getProductId(), List.of(RentalStatus.ACTIVE, RentalStatus.OVERDUE));
        if (alreadyRented) {
            throw new IllegalStateException(
                    "Product is currently rented out and not yet returned");
        }

        // Per-day pricing takes priority when set on the product; otherwise
        // fall back to the flat one-time rentalPrice fee (legacy behavior,
        // still used by any product without a per-day rate configured).
        BigDecimal rentalFee;
        long days = ChronoUnit.DAYS.between(req.getRentalStart(), req.getRentalEnd());
        if (product.getRentalPricePerDay() != null) {
            rentalFee = product.getRentalPricePerDay().multiply(BigDecimal.valueOf(days));
        } else {
            rentalFee = product.getRentalPrice();
        }

        String imageUrl = (product.getImages() != null && !product.getImages().isEmpty())
                ? product.getImages().get(0).getUrl()
                : null;

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(1)
                .unitPrice(rentalFee)
                .productName(product.getName())
                .productImage(imageUrl)
                .build();

        Order syntheticOrder = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .totalAmount(rentalFee)
                .notes("Rental fee — auto-generated, not a customer purchase order")
                .orderMode(OrderMode.WEBSITE)
                .paymentMethod(PaymentMethod.CASH) // rentals are cash-only — decided this session
                .isRentalDeposit(true)
                .items(List.of(item))
                .build();

        item.setOrder(syntheticOrder);

        Order savedOrder = orderRepository.save(syntheticOrder);

        Rental rental = Rental.builder()
                .user(user)
                .product(product)
                .order(savedOrder)
                .rentalStart(req.getRentalStart())
                .rentalEnd(req.getRentalEnd())
                .depositAmount(rentalFee)
                .status(RentalStatus.PENDING_PAYMENT)
                .build();

        Rental savedRental = rentalRepository.save(rental);

        log.info("[Rental] Customer {} booked rental for product {} — synthetic order {} created, {} days, rental fee LKR {} (cash, due at pickup)",
                callerId, req.getProductId(), savedOrder.getId(), days, rentalFee);

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
    public RentalResponse markReturned(UUID id, LocalDate returnDate) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rental not found: " + id));

        rental.setStatus(RentalStatus.RETURNED);
        rental.setReturnDate(returnDate);
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
        boolean isStaff = "ROLE_ADMIN".equals(role) || "ROLE_EMPLOYEE".equals(role);
        if (!isOwner && !isStaff) {
            throw new UnauthorizedException("Not authorized to cancel this rental");
        }

        if (rental.getStatus() != RentalStatus.PENDING_PAYMENT && rental.getStatus() != RentalStatus.BOOKED) {
            throw new ConflictException("This rental can no longer be cancelled");
        }

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
        List<Rental> toActivate = rentalRepository
                .findByStatusAndRentalStartLessThanEqual(RentalStatus.BOOKED, LocalDate.now());

        if (toActivate.isEmpty()) {
            log.info("[RentalScheduler] No rentals to activate.");
            return;
        }

        for (Rental rental : toActivate) {
            rental.setStatus(RentalStatus.ACTIVE);
            rentalRepository.save(rental);
            log.info("[RentalScheduler] Rental {} transitioned BOOKED -> ACTIVE (start date {} reached).",
                    rental.getId(), rental.getRentalStart());
        }

        log.info("[RentalScheduler] Activated {} rental(s).", toActivate.size());
    }

    // ─── New: 48h auto-expiry of unpaid bookings ───────────────────────────
    // Decided this session: item stays unlocked/unreserved until cash is paid.
    // If nobody's paid by 48h after the requested pickup (rentalStart) date,
    // cancel the booking and its synthetic order so it stops cluttering the
    // admin dashboard. Reuses the same repo method markActiveRentals() uses,
    // just against PENDING_PAYMENT with a 2-day-old cutoff (rentalStart is a
    // LocalDate, so date-level granularity is the best available precision).
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
            rentalRepository.save(rental);

            Order order = rental.getOrder();
            if (order != null && order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
            }

            log.info("[RentalScheduler] Rental {} expired — no cash payment received within 48h of requested pickup date {}.",
                    rental.getId(), rental.getRentalStart());
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

        boolean isStaff = role != null &&
                (role.equals("ROLE_ADMIN") || role.equals("ADMIN") ||
                        role.equals("ROLE_EMPLOYEE") || role.equals("EMPLOYEE"));

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

        // Per-day rate overrides the flat fee when set (Product entity's own
        // doc comment on rentalPricePerDay).
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

        // NO stock decrement — rentals don't touch Product.stock (confirmed
        // in handover doc §2.4 point 3 / OrderServiceImpl's own comments).
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
                .depositAmount(fee)
                .balanceDue(BigDecimal.ZERO)
                .notes(req.getNotes())
                .build();
        rentalRepository.save(rental);

        log.info("[Rental] Created rental booking — order {} / product {} for customer {} (created by {}) — deposit LKR {}",
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
                .rentalStart(rental.getRentalStart())
                .rentalEnd(rental.getRentalEnd())
                .returnDate(rental.getReturnDate())
                .status(rental.getStatus())
                .depositAmount(rental.getDepositAmount())
                .balanceDue(rental.getBalanceDue())
                .notes(rental.getNotes())
                .createdAt(rental.getCreatedAt())
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
    // here rather than made public on OrderService — keeps RentalService
    // independent and avoids widening OrderService's contract for one caller.
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
