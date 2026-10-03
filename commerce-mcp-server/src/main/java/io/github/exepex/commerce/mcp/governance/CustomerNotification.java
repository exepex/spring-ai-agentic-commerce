package io.github.exepex.commerce.mcp.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A message to a customer. The demo shows it in the UI's customer inbox rather than emailing it. */
@Entity
@Table(name = "customer_notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerNotification {

    @Id
    @Getter
    private UUID id;

    @Column(name = "order_id")
    @Getter
    private UUID orderId;

    @Column(name = "customer_email")
    @Getter
    private String customerEmail;

    @Getter
    private String message;

    @Column(name = "sent_by")
    @Getter
    private String sentBy;

    @Column(name = "created_at")
    @Getter
    private Instant createdAt;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

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
}
