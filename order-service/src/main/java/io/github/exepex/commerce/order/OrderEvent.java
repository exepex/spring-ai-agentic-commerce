package io.github.exepex.commerce.order;

import java.time.Instant;
import java.util.UUID;

/** Published on the {@code order.events} topic, keyed by order id, once the change behind it has committed. */
public record OrderEvent(UUID eventId, Type type, UUID orderId, String customerEmail, Instant occurredAt) {

    public enum Type {
        ORDER_CONFIRMED,
        ORDER_CANCELLED
    }

    static OrderEvent of(Type type, CustomerOrder order, Instant now) {
        return new OrderEvent(UUID.randomUUID(), type, order.getId(), order.getCustomerEmail(), now);
    }
}
