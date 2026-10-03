package io.github.exepex.commerce.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Units of one product held for one order. They stay reserved until the order ships, when they leave the warehouse
 * ({@code DISPATCHED}), or is cancelled ({@code RELEASED}).
 */
@Entity
@Table(name = "stock_reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    StockReservation(UUID orderId, UUID productId, int quantity, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = Status.RESERVED;
        this.createdAt = createdAt;
    }

    /** A repeated reservation must still be held, and for the same quantity as the first one. */
    boolean isSameReservationAs(int otherQuantity) {
        return status == Status.RESERVED && quantity == otherQuantity;
    }

    void markDispatched() {
        status = Status.DISPATCHED;
    }

    void markReleased() {
        status = Status.RELEASED;
    }
}
