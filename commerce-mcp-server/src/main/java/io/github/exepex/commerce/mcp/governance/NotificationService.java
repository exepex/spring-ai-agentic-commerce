package io.github.exepex.commerce.mcp.governance;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    /** A notification, and whether this call sent it or an earlier one with the same key did. */
    public record Sent(CustomerNotification notification, boolean now) {}

    /** The longest key the database stores. */
    private static final int MAX_KEY_LENGTH = 200;

    private final CustomerNotificationRepository notifications;
    private final AuditTrail audit;
    private final JdbcClient jdbc;
    private final Clock clock;

    /**
     * Sends a message to the customer. With a key, the message is sent once: asking again with the same key returns
     * the first notification and sends nothing. The notification and its audit entry are saved together or not at all.
     */
    @Transactional
    public Sent notifyCustomer(String sentBy, UUID orderId, String customerEmail, String message, String idempotencyKey) {
        if (idempotencyKey != null) {
            if (idempotencyKey.length() > MAX_KEY_LENGTH) {
                throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "An idempotency key can be at most " + MAX_KEY_LENGTH + " characters.");
            }
            // Two sends with the same key at once must not both miss the other's notification.
            AdvisoryLocks.lock(jdbc, idempotencyKey, 3);
            Optional<CustomerNotification> sent = notifications.findByIdempotencyKey(idempotencyKey);
            if (sent.isPresent()) {
                audit.record(orderId, AuditEvent.ActorType.AGENT, sentBy, "notify_customer", AuditEvent.Outcome.SUCCEEDED,
                        "Already notified " + customerEmail + " with key " + idempotencyKey + "; nothing sent again",
                        message);
                return new Sent(sent.get(), false);
            }
        }
        CustomerNotification notification = notifications.save(
                new CustomerNotification(orderId, customerEmail, message, sentBy, Instant.now(clock), idempotencyKey));
        audit.record(orderId, AuditEvent.ActorType.AGENT, sentBy, "notify_customer", AuditEvent.Outcome.SUCCEEDED,
                "Notified " + customerEmail, message);
        return new Sent(notification, true);
    }

    public List<CustomerNotification> forOrder(UUID orderId) {
        return notifications.findByOrderIdOrderByCreatedAt(orderId);
    }

    public List<CustomerNotification> recent() {
        return notifications.findTop100ByOrderByCreatedAtDesc();
    }
}
