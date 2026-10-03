package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.constants.ConfigKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Lets the order follow its parcel: delivered, not delivered, or lost. A report that loses a race with another change
 * to the order fails and is delivered again by Kafka.
 */
@Component
@RequiredArgsConstructor
class ShipmentEventListener {

    private final OrderService orderService;

    @KafkaListener(topics = ConfigKeys.SHIPMENT_EVENTS_TOPIC)
    void onShipmentEvent(ShipmentEvent event) {
        orderService.recordCarrierOutcome(event.orderId(), event.type().orderStatus());
    }
}
