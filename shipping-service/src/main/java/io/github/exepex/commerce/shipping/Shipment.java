package io.github.exepex.commerce.shipping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An order's parcel: prepared when the order is confirmed, handed to the carrier when the order ships, then delivered,
 * not delivered, or lost, as the carrier reports. A parcel that has not shipped yet is cancelled with its order.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    public enum Status {
        PREPARING,
        SHIPPED,
        DELIVERED,
        DELIVERY_FAILED,
        LOST,
        CANCELLED;

        /** What the carrier can report about a shipped parcel. */
        boolean isCarrierOutcome() {
            return this == DELIVERED || this == DELIVERY_FAILED || this == LOST;
        }
    }

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "estimated_delivery")
    private LocalDate estimatedDelivery;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** Why the carrier could not deliver the parcel, or that it lost it. */
    @Column(name = "delivery_problem")
    private String deliveryProblem;

    Shipment(UUID orderId, String customerEmail, String trackingNumber, LocalDate estimatedDelivery, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.trackingNumber = trackingNumber;
        this.status = Status.PREPARING;
        this.estimatedDelivery = estimatedDelivery;
        this.createdAt = createdAt;
    }

    /** The order service refuses to cancel an order that has shipped, so only a parcel being prepared is cancelled. */
    void cancel(Instant now) {
        if (status == Status.PREPARING) {
            status = Status.CANCELLED;
            cancelledAt = now;
        }
    }

    void ship(Instant now) {
        if (status == Status.PREPARING) {
            status = Status.SHIPPED;
            shippedAt = now;
        }
    }

    void recordCarrierOutcome(Status outcome, String problem, Instant now) {
        status = outcome;
        if (outcome == Status.DELIVERED) {
            deliveredAt = now;
        } else {
            deliveryProblem = problem;
        }
    }
}
