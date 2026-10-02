package io.github.exepex.commerce.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Stock reserved for an order that must go back to the catalog: kept until the catalog confirms the release. */
@Entity
@Table(name = "stock_release")
class StockRelease {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "requested_at")
    private Instant requestedAt;

    protected StockRelease() {
        // for JPA
    }

    StockRelease(UUID orderId, Instant requestedAt) {
        this.orderId = orderId;
        this.requestedAt = requestedAt;
    }

    UUID getOrderId() {
        return orderId;
    }
}
