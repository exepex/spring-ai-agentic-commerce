package io.github.exepex.commerce.order;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finishes what checkout and cancellation could not: it settles orders whose payment outcome is unknown, by asking
 * the payment service for the same idempotent payment again, and retries stock releases the catalog has not
 * confirmed. An order only counts as stalled once it is older than {@code commerce.reconciliation.settle-after}, so
 * a checkout that is still running is left alone.
 */
@Component
class OrderReconciler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderReconciler.class);

    private final CustomerOrderRepository orders;
    private final OrderService orderService;
    private final StockReleases releases;
    private final Duration settleAfter;
    private final Clock clock;

    OrderReconciler(CustomerOrderRepository orders, OrderService orderService, StockReleases releases,
            @Value("${commerce.reconciliation.settle-after}") Duration settleAfter, Clock clock) {
        this.orders = orders;
        this.orderService = orderService;
        this.releases = releases;
        this.settleAfter = settleAfter;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${commerce.reconciliation.interval}", initialDelayString = "${commerce.reconciliation.interval}")
    void reconcile() {
        Instant stalledBefore = Instant.now(clock).minus(settleAfter);
        for (CustomerOrder order : orders.findByStatusInAndCreatedAtBefore(
                List.of(OrderStatus.PLACED, OrderStatus.PAYMENT_PENDING), stalledBefore)) {
            try {
                orderService.settlePayment(order);
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not settle the payment of order {}; it will be retried", order.getId(), failure);
            }
        }
        releases.retryAll();
    }
}
