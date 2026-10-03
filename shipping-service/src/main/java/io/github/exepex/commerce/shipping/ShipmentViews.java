package io.github.exepex.commerce.shipping;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the shipping API answers with, and how a shipment turns into it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ShipmentViews {

    record ShipmentView(UUID id, UUID orderId, String customerEmail, String trackingNumber, String status,
            LocalDate estimatedDelivery, Instant createdAt, Instant shippedAt, Instant deliveredAt,
            String deliveryProblem, Instant cancelledAt) {}

    static ShipmentView toView(Shipment shipment) {
        return new ShipmentView(shipment.getId(), shipment.getOrderId(), shipment.getCustomerEmail(),
                shipment.getTrackingNumber(), shipment.getStatus().name(), shipment.getEstimatedDelivery(),
                shipment.getCreatedAt(), shipment.getShippedAt(), shipment.getDeliveredAt(),
                shipment.getDeliveryProblem(), shipment.getCancelledAt());
    }
}
