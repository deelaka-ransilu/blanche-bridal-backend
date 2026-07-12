package com.blanchebridal.backend.order.service.impl;

import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderItem;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.payment.entity.PaymentMethod;
import com.blanchebridal.backend.product.entity.Product;
import com.blanchebridal.backend.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Cancels PENDING PayHere orders that were never completed within the
// expiry window, and restores the stock they reserved at creation time
// (same restoreStock logic/intent as OrderServiceImpl's customer-initiated
// cancelOrder — duplicated here rather than injecting OrderServiceImpl to
// avoid a circular/awkward dependency; if this drifts out of sync with that
// method, consolidate then).
//
// CASH orders are deliberately excluded — those are staff-assisted, paid
// in person, and managed manually by admin, not abandoned checkouts.
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleOrderExpiryJob {

    private static final int EXPIRY_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    // Runs every 5 minutes. Adjust fixedRate if 30-minute granularity turns
    // out too coarse/fine in practice.
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void expireStalePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(EXPIRY_MINUTES);

        List<Order> stale = orderRepository.findByStatusAndPaymentMethodAndCreatedAtBefore(
                OrderStatus.PENDING, PaymentMethod.PAYHERE, cutoff);

        if (stale.isEmpty()) return;

        log.info("[OrderExpiry] Found {} stale PENDING PayHere order(s) past {}-minute window",
                stale.size(), EXPIRY_MINUTES);

        for (Order order : stale) {
            restoreStock(order);
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            log.info("[OrderExpiry] Cancelled abandoned order {} (created {})",
                    order.getId(), order.getCreatedAt());
        }
    }

    private void restoreStock(Order order) {
        if (order.getItems() == null) return;
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null) continue;
            Product locked = productRepository.findByIdForUpdate(product.getId()).orElse(null);
            if (locked == null) continue;
            locked.setStock(locked.getStock() + item.getQuantity());
            productRepository.save(locked);
        }
    }
}