package io.github.exepex.commerce.mcp.governance;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final CustomerNotificationRepository notifications;
    private final AuditTrail audit;
    private final Clock clock;

    NotificationService(CustomerNotificationRepository notifications, AuditTrail audit, Clock clock) {
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    public CustomerNotification notifyCustomer(String sentBy, UUID orderId, String customerEmail, String message) {
        CustomerNotification notification = notifications.save(
                new CustomerNotification(orderId, customerEmail, message, sentBy, Instant.now(clock)));
        audit.record(orderId, AuditEvent.ActorType.AGENT, sentBy, "notify_customer", AuditEvent.Outcome.SUCCEEDED,
                "Notified " + customerEmail, message);
        return notification;
    }

    public List<CustomerNotification> forOrder(UUID orderId) {
        return notifications.findByOrderIdOrderByCreatedAt(orderId);
    }

    public List<CustomerNotification> recent() {
        return notifications.findTop100ByOrderByCreatedAtDesc();
    }
}
