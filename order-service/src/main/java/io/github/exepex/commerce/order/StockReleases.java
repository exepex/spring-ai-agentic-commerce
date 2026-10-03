package io.github.exepex.commerce.order;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gives an order's reserved stock back to the catalog, reliably. {@link #request} records the release in the
 * caller's transaction, {@link #attempt} tries it once the transaction has committed, and {@link #retryAll} retries
 * whatever is still recorded. Releasing is idempotent in the catalog, so trying again is always safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class StockReleases {

    private final StockReleaseRepository releases;
    private final CatalogGateway catalog;
    private final Clock clock;

    /** Records that the order's stock must be released. Call it inside the transaction that makes it necessary. */
    void request(UUID orderId) {
        releases.insertIfAbsent(orderId, Instant.now(clock));
    }

    /** Releases the order's stock now; if the catalog cannot be reached, the release stays recorded for later. */
    void attempt(UUID orderId) {
        try {
            catalog.releaseOrderReservations(orderId);
            releases.deleteById(orderId);
        } catch (DependencyUnavailableException catalogDown) {
            log.warn("Could not release the stock reserved for order {} yet; it will be retried", orderId);
        }
    }

    void retryAll() {
        releases.findAll().forEach(release -> attempt(release.getOrderId()));
    }
}
