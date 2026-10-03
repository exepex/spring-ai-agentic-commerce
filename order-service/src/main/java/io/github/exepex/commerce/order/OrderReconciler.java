package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.constants.ConfigKeys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finishes what checkout and cancellation could not: it settles orders whose payment outcome is unknown, by asking
 * the payment service for the same idempotent payment again, and retries stock releases the catalog has not
 * confirmed. An order only counts as stalled once it is older than {@code commerce.reconciliation.settle-after}, so
 * a checkout that is still running is left alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OrderReconciler {

    private final CustomerOrderRepository orders;
    private final OrderService orderService;
    private final StockReleases releases;

    @Value(ConfigKeys.RECONCILIATION_SETTLE_AFTER)
    private final Duration settleAfter;

    private final Clock clock;

    @Scheduled(fixedDelayString = ConfigKeys.RECONCILIATION_INTERVAL,
            initialDelayString = ConfigKeys.RECONCILIATION_INTERVAL)
    void reconcile() {
        var stalledBefore = Instant.now(clock).minus(settleAfter);
        for (var order : orders.findByStatusInAndCreatedAtBefore(
                List.of(OrderStatus.PLACED, OrderStatus.PAYMENT_PENDING), stalledBefore)) {
            try {
                orderService.settlePayment(order);
            } catch (RuntimeException failure) {
                log.warn("Could not settle the payment of order {}; it will be retried", order.getId(), failure);
            }
        }
        releases.retryAll();
    }
}
