package io.github.exepex.commerce.shipping;

import java.time.Instant;
import java.util.UUID;

/** An order-service event on the {@code order.events} topic, as JSON. */
public record OrderEvent(UUID eventId, Type type, UUID orderId, String customerEmail, Instant occurredAt) {

    public enum Type {
        ORDER_CONFIRMED,
        ORDER_CANCELLED
    }
}
