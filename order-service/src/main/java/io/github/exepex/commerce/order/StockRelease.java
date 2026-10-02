package io.github.exepex.commerce.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Stock reserved for an order that must go back to the catalog: kept until the catalog confirms the release. */
@Entity
@Table(name = "stock_release")
class StockRelease {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    protected StockRelease() {
        // for JPA; rows are written by StockReleaseRepository.insertIfAbsent
    }

    UUID getOrderId() {
        return orderId;
    }
}
