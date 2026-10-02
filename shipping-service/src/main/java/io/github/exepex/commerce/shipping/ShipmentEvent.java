package io.github.exepex.commerce.shipping;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on the {@code shipment.events} topic, keyed by order id, once the carrier's report has been saved.
 * {@code deliveryProblem} says why a parcel was not delivered; it is empty for a delivered one.
 */
public record ShipmentEvent(UUID eventId, Type type, UUID orderId, String customerEmail, String trackingNumber,
        String deliveryProblem, Instant occurredAt) {

    public enum Type {
        SHIPMENT_DELIVERED,
        SHIPMENT_DELIVERY_FAILED,
        SHIPMENT_LOST
    }

    static ShipmentEvent of(Shipment shipment, Instant now) {
        Type type = switch (shipment.getStatus()) {
            case DELIVERED -> Type.SHIPMENT_DELIVERED;
            case DELIVERY_FAILED -> Type.SHIPMENT_DELIVERY_FAILED;
            case LOST -> Type.SHIPMENT_LOST;
            default -> throw new IllegalStateException("The carrier did not report on shipment " + shipment.getId());
        };
        return new ShipmentEvent(UUID.randomUUID(), type, shipment.getOrderId(), shipment.getCustomerEmail(),
                shipment.getTrackingNumber(), shipment.getDeliveryProblem(), now);
    }
}
