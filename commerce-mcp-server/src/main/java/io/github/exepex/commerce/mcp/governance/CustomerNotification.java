package io.github.exepex.commerce.mcp.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A message to a customer. The demo shows it in the UI's customer inbox rather than emailing it. */
@Entity
@Table(name = "customer_notification")
public class CustomerNotification {

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_email")
    private String customerEmail;

    private String message;

    @Column(name = "sent_by")
    private String sentBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    protected CustomerNotification() {
        // for JPA
    }

    CustomerNotification(UUID orderId, String customerEmail, String message, String sentBy, Instant createdAt,
            String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.message = message;
        this.sentBy = sentBy;
        this.createdAt = createdAt;
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

    public String getMessage() {
        return message;
    }

    public String getSentBy() {
        return sentBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
