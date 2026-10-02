package io.github.exepex.commerce.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Units of one product held for one order. They stay reserved until the order ships, when they leave the warehouse
 * ({@code DISPATCHED}), or is cancelled ({@code RELEASED}).
 */
@Entity
@Table(name = "stock_reservation")
public class StockReservation {

    enum Status {
        RESERVED,
        DISPATCHED,
        RELEASED
    }

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "product_id")
    private UUID productId;

    private int quantity;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "created_at")
    private Instant createdAt;

    protected StockReservation() {
        // for JPA
    }

    StockReservation(UUID orderId, UUID productId, int quantity, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = Status.RESERVED;
        this.createdAt = createdAt;
    }

    void markDispatched() {
        status = Status.DISPATCHED;
    }

    void markReleased() {
        status = Status.RELEASED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
