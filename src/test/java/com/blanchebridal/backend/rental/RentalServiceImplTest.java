package com.blanchebridal.backend.rental;

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
import com.blanchebridal.backend.rental.service.impl.RentalServiceImpl;
import com.blanchebridal.backend.shared.email.EmailService;
import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.entity.UserRole;
import com.blanchebridal.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RentalServiceImpl Tests")
class RentalServiceImplTest {

    @Mock private RentalRepository rentalRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private RentalServiceImpl rentalService;

    private User customer;
    private Product product;
    private Rental rental;
    private UUID customerId;
    private UUID productId;
    private UUID rentalId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        productId  = UUID.randomUUID();
        rentalId   = UUID.randomUUID();

        customer = User.builder()
                .id(customerId)
                .email("customer@example.com")
                .firstName("Amaya")
                .lastName("Silva")
                .role(UserRole.CUSTOMER)
                .isActive(true)
                .build();

        product = Product.builder()
                .id(productId)
                .name("Ivory Lace Gown")
                .images(new ArrayList<>())
                .build();

        rental = Rental.builder()
                .id(rentalId)
                .user(customer)
                .product(product)
                .rentalStart(LocalDate.now())
                .rentalEnd(LocalDate.now().plusDays(7))
                .status(RentalStatus.ACTIVE)
                .balanceDue(BigDecimal.ZERO)
                .depositAmount(new BigDecimal("5000.00"))
                .build();
    }

    // ── createRental ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("createRental — creates rental with status ACTIVE")
    void createRental_validRequest_createsWithActiveStatus() {
        CreateRentalRequest req = new CreateRentalRequest();
        req.setUserId(customerId);
        req.setProductId(productId);
        req.setRentalStart(LocalDate.now());
        req.setRentalEnd(LocalDate.now().plusDays(7));
        req.setDepositAmount(new BigDecimal("5000.00"));

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(rentalRepository.existsByProduct_IdAndStatusIn(productId,
                List.of(RentalStatus.ACTIVE, RentalStatus.OVERDUE))).thenReturn(false);
        when(rentalRepository.save(any(Rental.class))).thenReturn(rental);

        RentalResponse response = rentalService.createRental(req);

        assertThat(response.getStatus()).isEqualTo(RentalStatus.ACTIVE);
        assertThat(response.getProductName()).isEqualTo("Ivory Lace Gown");
        assertThat(response.getCustomerName()).isEqualTo("Amaya Silva");
        verify(rentalRepository).save(any(Rental.class));
    }

    @Test
    @DisplayName("createRental — throws IllegalStateException when product already rented")
    void createRental_productAlreadyRented_throwsException() {
        CreateRentalRequest req = new CreateRentalRequest();
        req.setUserId(customerId);
        req.setProductId(productId);
        req.setRentalStart(LocalDate.now());
        req.setRentalEnd(LocalDate.now().plusDays(7));

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(rentalRepository.existsByProduct_IdAndStatusIn(productId,
                List.of(RentalStatus.ACTIVE, RentalStatus.OVERDUE))).thenReturn(true);

        assertThatThrownBy(() -> rentalService.createRental(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currently rented out");

        verify(rentalRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRental — throws ResourceNotFoundException when user not found")
    void createRental_userNotFound_throwsException() {
        CreateRentalRequest req = new CreateRentalRequest();
        req.setUserId(customerId);
        req.setProductId(productId);

        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rentalService.createRental(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("createRental — throws ResourceNotFoundException when product not found")
    void createRental_productNotFound_throwsException() {
        CreateRentalRequest req = new CreateRentalRequest();
        req.setUserId(customerId);
        req.setProductId(productId);

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rentalService.createRental(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    @DisplayName("createRental — links order when orderId is provided")
    void createRental_withOrderId_linksOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).build();

        CreateRentalRequest req = new CreateRentalRequest();
        req.setUserId(customerId);
        req.setProductId(productId);
        req.setRentalStart(LocalDate.now());
        req.setRentalEnd(LocalDate.now().plusDays(7));
        req.setOrderId(orderId);

        Rental rentalWithOrder = Rental.builder()
                .id(rentalId)
                .user(customer)
                .product(product)
                .order(order)
                .rentalStart(LocalDate.now())
                .rentalEnd(LocalDate.now().plusDays(7))
                .status(RentalStatus.ACTIVE)
                .balanceDue(BigDecimal.ZERO)
                .build();

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(rentalRepository.existsByProduct_IdAndStatusIn(any(), any())).thenReturn(false);
        when(rentalRepository.save(any(Rental.class))).thenReturn(rentalWithOrder);

        RentalResponse response = rentalService.createRental(req);

        assertThat(response.getOrderId()).isEqualTo(orderId);
    }

    // ── getRentalById ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getRentalById — admin can access any rental")
    void getRentalById_adminRole_returnsRental() {
        when(rentalRepository.findById(rentalId)).thenReturn(Optional.of(rental));

        RentalResponse response = rentalService.getRentalById(rentalId, UUID.randomUUID(), "ROLE_ADMIN");

        assertThat(response.getId()).isEqualTo(rentalId);
    }

    @Test
    @DisplayName("getRentalById — customer can access own rental")
    void getRentalById_customerOwnRental_returnsRental() {
        when(rentalRepository.findById(rentalId)).thenReturn(Optional.of(rental));

        RentalResponse response = rentalService.getRentalById(rentalId, customerId, "ROLE_CUSTOMER");

        assertThat(response.getId()).isEqualTo(rentalId);
    }

    @Test
    @DisplayName("getRentalById — customer cannot access another customer's rental")
    void getRentalById_customerOtherRental_throwsException() {
        when(rentalRepository.findById(rentalId)).thenReturn(Optional.of(rental));

        UUID otherId = UUID.randomUUID();

        assertThatThrownBy(() ->
                rentalService.getRentalById(rentalId, otherId, "ROLE_CUSTOMER"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("getRentalById — throws ResourceNotFoundException when not found")
    void getRentalById_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(rentalRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                rentalService.getRentalById(unknownId, customerId, "ROLE_ADMIN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    // ── markReturned ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("markReturned — sets status RETURNED and returnDate")
    void markReturned_activeRental_setsReturnedStatusAndDate() {
        LocalDate returnDate = LocalDate.now();
        Rental returned = Rental.builder()
                .id(rentalId)
                .user(customer)
                .product(product)
                .rentalStart(rental.getRentalStart())
                .rentalEnd(rental.getRentalEnd())
                .returnDate(returnDate)
                .status(RentalStatus.RETURNED)
                .balanceDue(BigDecimal.ZERO)
                .build();

        when(rentalRepository.findById(rentalId)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any(Rental.class))).thenReturn(returned);

        RentalResponse response = rentalService.markReturned(rentalId, returnDate);

        assertThat(response.getStatus()).isEqualTo(RentalStatus.RETURNED);
        assertThat(response.getReturnDate()).isEqualTo(returnDate);
    }

    @Test
    @DisplayName("markReturned — throws ResourceNotFoundException when rental not found")
    void markReturned_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(rentalRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rentalService.markReturned(unknownId, LocalDate.now()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    // ── updateBalance ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateBalance — updates balanceDue correctly")
    void updateBalance_validRequest_updatesBalance() {
        BigDecimal newBalance = new BigDecimal("1500.00");
        Rental updated = Rental.builder()
                .id(rentalId)
                .user(customer)
                .product(product)
                .rentalStart(rental.getRentalStart())
                .rentalEnd(rental.getRentalEnd())
                .status(RentalStatus.ACTIVE)
                .balanceDue(newBalance)
                .build();

        UpdateBalanceRequest req = new UpdateBalanceRequest();
        req.setBalanceDue(newBalance);

        when(rentalRepository.findById(rentalId)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any(Rental.class))).thenReturn(updated);

        RentalResponse response = rentalService.updateBalance(rentalId, req);

        assertThat(response.getBalanceDue()).isEqualByComparingTo("1500.00");
    }

    // ── markOverdueRentals ────────────────────────────────────────────────────

    @Test
    @DisplayName("markOverdueRentals — marks expired active rentals as OVERDUE and sends emails")
    void markOverdueRentals_withExpiredRentals_marksOverdueAndSendsEmails() {
        Rental expiredRental = Rental.builder()
                .id(UUID.randomUUID())
                .user(customer)
                .product(product)
                .rentalStart(LocalDate.now().minusDays(10))
                .rentalEnd(LocalDate.now().minusDays(1))
                .status(RentalStatus.ACTIVE)
                .balanceDue(BigDecimal.ZERO)
                .build();

        when(rentalRepository.findByStatusAndRentalEndBefore(
                eq(RentalStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(expiredRental));
        when(rentalRepository.save(any(Rental.class))).thenReturn(expiredRental);

        rentalService.markOverdueRentals();

        assertThat(expiredRental.getStatus()).isEqualTo(RentalStatus.OVERDUE);
        verify(rentalRepository).save(expiredRental);
        verify(emailService).sendRentalOverdueEmail(
                eq(customer.getEmail()),
                eq("Amaya Silva"),
                eq("Ivory Lace Gown"),
                any(LocalDate.class),
                any(BigDecimal.class));
    }

    @Test
    @DisplayName("markOverdueRentals — does nothing when no expired rentals found")
    void markOverdueRentals_noExpiredRentals_doesNothing() {
        when(rentalRepository.findByStatusAndRentalEndBefore(
                eq(RentalStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of());

        rentalService.markOverdueRentals();

        verify(rentalRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("markOverdueRentals — email failure does not stop remaining rentals")
    void markOverdueRentals_emailFails_continuesProcessing() {
        Rental rental1 = Rental.builder()
                .id(UUID.randomUUID())
                .user(customer)
                .product(product)
                .rentalEnd(LocalDate.now().minusDays(1))
                .status(RentalStatus.ACTIVE)
                .balanceDue(BigDecimal.ZERO)
                .build();

        Rental rental2 = Rental.builder()
                .id(UUID.randomUUID())
                .user(customer)
                .product(product)
                .rentalEnd(LocalDate.now().minusDays(2))
                .status(RentalStatus.ACTIVE)
                .balanceDue(BigDecimal.ZERO)
                .build();

        when(rentalRepository.findByStatusAndRentalEndBefore(
                eq(RentalStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(rental1, rental2));
        when(rentalRepository.save(any(Rental.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("SMTP error"))
                .when(emailService).sendRentalOverdueEmail(any(), any(), any(), any(), any());

        rentalService.markOverdueRentals();

        // Both rentals still marked OVERDUE despite email failures
        assertThat(rental1.getStatus()).isEqualTo(RentalStatus.OVERDUE);
        assertThat(rental2.getStatus()).isEqualTo(RentalStatus.OVERDUE);
        verify(rentalRepository, times(2)).save(any(Rental.class));
    }
}