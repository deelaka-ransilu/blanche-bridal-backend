package com.blanchebridal.backend.order.scheduler;

import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs every 5 minutes and cancels any PENDING orders that have not been
 * paid within 30 minutes of creation.
 *
 * This covers the case where a customer:
 *   - Clicks "Place Order & Pay" but closes the browser before PayHere loads
 *   - Abandons the PayHere page without pressing cancel
 *   - Loses connectivity mid-payment
 *
 * Because stock is only deducted in PaymentServiceImpl.handleWebhook() on a
 * confirmed payment, cancelling a PENDING order here never needs to restore
 * stock — there is nothing to restore.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderRepository orderRepository;

    private static final int PENDING_TIMEOUT_MINUTES = 30;

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    @Transactional
    public void cancelStaleOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PENDING_TIMEOUT_MINUTES);

        List<Order> staleOrders = orderRepository
                .findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);

        if (staleOrders.isEmpty()) {
            log.debug("[OrderScheduler] No stale PENDING orders found.");
            return;
        }

        for (Order order : staleOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            log.info("[OrderScheduler] Auto-cancelled stale PENDING order {} (created at {})",
                    order.getId(), order.getCreatedAt());
        }

        log.info("[OrderScheduler] Auto-cancelled {} stale order(s).", staleOrders.size());
    }
}