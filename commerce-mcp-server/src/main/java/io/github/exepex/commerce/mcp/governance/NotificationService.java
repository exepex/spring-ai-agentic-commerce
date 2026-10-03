package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.constants.AuditActions;
import io.github.exepex.commerce.mcp.constants.AuditSummaries;
import io.github.exepex.commerce.mcp.exception.IdempotencyKeyTooLongException;
import io.github.exepex.commerce.mcp.governance.dto.Sent;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

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
                throw new IdempotencyKeyTooLongException(MAX_KEY_LENGTH);
            }
            // Two sends with the same key at once must not both miss the other's notification.
            AdvisoryLocks.lock(jdbc, idempotencyKey, 3);
            var sent = notifications.findByIdempotencyKey(idempotencyKey);
            if (sent.isPresent()) {
                audit.record(orderId, AuditEvent.ActorType.AGENT, sentBy, AuditActions.NOTIFY_CUSTOMER,
                        AuditEvent.Outcome.SUCCEEDED, AuditSummaries.ALREADY_NOTIFIED.formatted(customerEmail,
                                idempotencyKey), message);
                return new Sent(sent.get(), false);
            }
        }
        var notification = notifications.save(
                new CustomerNotification(orderId, customerEmail, message, sentBy, Instant.now(clock), idempotencyKey));
        audit.record(orderId, AuditEvent.ActorType.AGENT, sentBy, AuditActions.NOTIFY_CUSTOMER,
                AuditEvent.Outcome.SUCCEEDED, AuditSummaries.NOTIFIED.formatted(customerEmail), message);
        return new Sent(notification, true);
    }

    public List<CustomerNotification> forOrder(UUID orderId) {
        return notifications.findByOrderIdOrderByCreatedAt(orderId);
    }

    public List<CustomerNotification> recent() {
        return notifications.findTop100ByOrderByCreatedAtDesc();
    }
}
