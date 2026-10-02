package io.github.exepex.commerce.order;

import java.time.Instant;
import java.util.UUID;

/** A shipping-service event on the {@code shipment.events} topic, as JSON: what the carrier reported. */
public record ShipmentEvent(UUID eventId, Type type, UUID orderId, Instant occurredAt) {

    public enum Type {
        SHIPMENT_DELIVERED(OrderStatus.DELIVERED),
        SHIPMENT_DELIVERY_FAILED(OrderStatus.DELIVERY_FAILED),
        SHIPMENT_LOST(OrderStatus.LOST);

        private final OrderStatus orderStatus;

        Type(OrderStatus orderStatus) {
            this.orderStatus = orderStatus;
        }

        /** The status the order takes when the carrier reports this. */
        OrderStatus orderStatus() {
            return orderStatus;
        }
    }
}
