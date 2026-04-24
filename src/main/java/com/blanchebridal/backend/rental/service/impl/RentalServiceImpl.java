package com.blanchebridal.backend.rental.service.impl;

import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.repository.ProductRepository;
import com.blanchebridal.backend.rental.dto.req.CreateRentalRequest;
import com.blanchebridal.backend.rental.dto.req.UpdateBalanceRequest;
import com.blanchebridal.backend.rental.dto.res.RentalResponse;
import com.blanchebridal.backend.rental.entity.Rental;
import com.blanchebridal.backend.rental.entity.RentalStatus;
import com.blanchebridal.backend.rental.repository.RentalRepository;
import com.blanchebridal.backend.rental.service.RentalService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentalServiceImpl implements RentalService {

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

        // Block if product is already out on an active or overdue rental
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
                .build();

        return toResponse(rentalRepository.save(rental));
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
}