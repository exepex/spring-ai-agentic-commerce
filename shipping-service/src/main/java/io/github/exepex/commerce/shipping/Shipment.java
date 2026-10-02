package io.github.exepex.commerce.shipping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
public class Shipment {

    enum Status {
        PREPARING,
        CANCELLED
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

    protected Shipment() {
        // for JPA
    }

    Shipment(UUID orderId, String customerEmail, String trackingNumber, LocalDate estimatedDelivery, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.trackingNumber = trackingNumber;
        this.status = Status.PREPARING;
        this.estimatedDelivery = estimatedDelivery;
        this.createdAt = createdAt;
    }

    void cancel(Instant now) {
        if (status != Status.CANCELLED) {
            status = Status.CANCELLED;
            cancelledAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getEstimatedDelivery() {
        return estimatedDelivery;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
