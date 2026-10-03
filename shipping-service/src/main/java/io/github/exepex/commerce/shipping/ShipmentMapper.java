package io.github.exepex.commerce.shipping;

import io.github.exepex.commerce.shipping.dto.ShipmentView;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How a shipment is shown through the shipping API. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ShipmentMapper {

    static ShipmentView toView(Shipment shipment) {
        return new ShipmentView(shipment.getId(), shipment.getOrderId(), shipment.getCustomerEmail(),
                shipment.getTrackingNumber(), shipment.getStatus().name(), shipment.getEstimatedDelivery(),
                shipment.getCreatedAt(), shipment.getShippedAt(), shipment.getDeliveredAt(),
                shipment.getDeliveryProblem(), shipment.getCancelledAt());
    }
}
