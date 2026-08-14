package com.blanchebridal.backend.report.service.impl;

import com.blanchebridal.backend.order.entity.DiscountType;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderStatus;
import com.blanchebridal.backend.order.repository.OrderRepository;
import com.blanchebridal.backend.refund.entity.Refund;
import com.blanchebridal.backend.refund.repository.RefundRepository;
import com.blanchebridal.backend.report.dto.DiscountReportItem;
import com.blanchebridal.backend.report.dto.RefundReportItem;
import com.blanchebridal.backend.report.dto.RevenueReportItem;
import com.blanchebridal.backend.report.dto.SummaryReport;
import com.blanchebridal.backend.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;

    private static final int DEFAULT_MONTHS_BACK = 12;

    private LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    private LocalDateTime endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }

    private LocalDate resolveFrom(LocalDate from) {
        return from != null ? from : LocalDate.now().minusMonths(DEFAULT_MONTHS_BACK).withDayOfMonth(1);
    }

    private LocalDate resolveTo(LocalDate to) {
        return to != null ? to : LocalDate.now();
    }

    /**
     * Revenue-counted orders = plain buy-flow orders that reached
     * OrderStatus.COMPLETED, PLUS rental and custom-design orders (each a
     * synthetic Order row) as soon as payment clears — which for those two
     * types means OrderStatus.CONFIRMED, since neither ever progresses past
     * CONFIRMED in its own lifecycle (confirmed by grepping every
     * order.setStatus(OrderStatus...) call site — the only other transition
     * either type undergoes is CANCELLED). A rental with two payments
     * (booking + handover) contributes two separate Order rows here, which
     * is correct — each is its own confirmed payment.
     */
    private List<Order> getRevenueCountedOrders(LocalDateTime start, LocalDateTime end) {
        List<Order> completedBuyOrders = orderRepository.findByStatusAndCreatedAtBetween(
                OrderStatus.COMPLETED, start, end);
        List<Order> confirmedRentalOrders = orderRepository.findByIsRentalDepositTrueAndStatusAndCreatedAtBetween(
                OrderStatus.CONFIRMED, start, end);
        List<Order> confirmedCustomOrders = orderRepository.findByIsCustomOrderTrueAndStatusAndCreatedAtBetween(
                OrderStatus.CONFIRMED, start, end);

        return Stream.of(completedBuyOrders, confirmedRentalOrders, confirmedCustomOrders)
                .flatMap(List::stream)
                .toList();
    }

    @Override
    public List<RevenueReportItem> getRevenueReport(LocalDate from, LocalDate to) {
        LocalDate resolvedFrom = resolveFrom(from);
        LocalDate resolvedTo = resolveTo(to);

        List<Order> revenueOrders = getRevenueCountedOrders(startOfDay(resolvedFrom), endOfDay(resolvedTo));

        Map<YearMonth, List<Order>> byMonth = revenueOrders.stream()
                .collect(Collectors.groupingBy(o -> YearMonth.from(o.getCreatedAt())));

        YearMonth start = YearMonth.from(resolvedFrom);
        YearMonth end = YearMonth.from(resolvedTo);

        List<RevenueReportItem> result = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            List<Order> monthOrders = byMonth.getOrDefault(m, List.of());
            result.add(RevenueReportItem.builder()
                    .month(m.toString())
                    .totalRevenue(monthOrders.stream()
                            .map(Order::getTotalAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .orderCount(monthOrders.size())
                    .build());
        }
        return result;
    }

    @Override
    public List<RefundReportItem> getRefundReport(LocalDate from, LocalDate to) {
        LocalDate resolvedFrom = resolveFrom(from);
        LocalDate resolvedTo = resolveTo(to);

        List<Refund> refunds = refundRepository.findByCreatedAtBetween(
                startOfDay(resolvedFrom), endOfDay(resolvedTo));

        Map<YearMonth, List<Refund>> byMonth = refunds.stream()
                .collect(Collectors.groupingBy(r -> YearMonth.from(r.getCreatedAt())));

        return byMonth.entrySet().stream()
                .map(e -> RefundReportItem.builder()
                        .month(e.getKey().toString())
                        .totalRefunded(e.getValue().stream()
                                .map(Refund::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .refundCount(e.getValue().size())
                        .build())
                .sorted(Comparator.comparing(RefundReportItem::getMonth))
                .toList();
    }

    @Override
    public List<DiscountReportItem> getDiscountReport(LocalDate from, LocalDate to) {
        LocalDate resolvedFrom = resolveFrom(from);
        LocalDate resolvedTo = resolveTo(to);

        List<Order> discountedOrders = orderRepository.findByDiscountTypeIsNotNullAndCreatedAtBetween(
                startOfDay(resolvedFrom), endOfDay(resolvedTo));

        Map<YearMonth, List<Order>> byMonth = discountedOrders.stream()
                .collect(Collectors.groupingBy(o -> YearMonth.from(o.getCreatedAt())));

        return byMonth.entrySet().stream()
                .map(e -> buildDiscountItem(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(DiscountReportItem::getMonth))
                .toList();
    }

    private DiscountReportItem buildDiscountItem(YearMonth month, List<Order> orders) {
        List<Order> fixed = orders.stream()
                .filter(o -> o.getDiscountType() == DiscountType.FIXED)
                .toList();
        List<Order> percentage = orders.stream()
                .filter(o -> o.getDiscountType() == DiscountType.PERCENTAGE)
                .toList();

        BigDecimal totalFixed = fixed.stream()
                .map(Order::getDiscountValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgPercentage = percentage.isEmpty()
                ? BigDecimal.ZERO
                : percentage.stream()
                .map(Order::getDiscountValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(percentage.size()), 2, RoundingMode.HALF_UP);

        return DiscountReportItem.builder()
                .month(month.toString())
                .fixedDiscountOrderCount(fixed.size())
                .totalFixedDiscountAmount(totalFixed)
                .percentageDiscountOrderCount(percentage.size())
                .averagePercentageDiscount(avgPercentage)
                .build();
    }

    @Override
    public SummaryReport getSummary(LocalDate from, LocalDate to) {
        LocalDate resolvedFrom = resolveFrom(from);
        LocalDate resolvedTo = resolveTo(to);
        LocalDateTime start = startOfDay(resolvedFrom);
        LocalDateTime end = endOfDay(resolvedTo);

        List<Order> revenueOrders = getRevenueCountedOrders(start, end);
        List<Refund> refunds = refundRepository.findByCreatedAtBetween(start, end);
        List<Order> discountedOrders = orderRepository.findByDiscountTypeIsNotNullAndCreatedAtBetween(start, end);

        BigDecimal totalRevenue = revenueOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRefunded = refunds.stream()
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long fixedCount = discountedOrders.stream()
                .filter(o -> o.getDiscountType() == DiscountType.FIXED)
                .count();
        long percentageCount = discountedOrders.stream()
                .filter(o -> o.getDiscountType() == DiscountType.PERCENTAGE)
                .count();
        BigDecimal totalFixedDiscount = discountedOrders.stream()
                .filter(o -> o.getDiscountType() == DiscountType.FIXED)
                .map(Order::getDiscountValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SummaryReport.builder()
                .from(resolvedFrom)
                .to(resolvedTo)
                .totalRevenue(totalRevenue)
                .completedOrderCount(revenueOrders.size())
                .totalRefunded(totalRefunded)
                .refundCount(refunds.size())
                .discountedOrderCount(discountedOrders.size())
                .totalFixedDiscountAmount(totalFixedDiscount)
                .percentageDiscountOrderCount(percentageCount)
                .build();
    }
}