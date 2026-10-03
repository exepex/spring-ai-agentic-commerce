package io.github.exepex.commerce.shipping;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends shipment events to Kafka after the change has committed. As in the other services, an event can be lost if
 * the process dies between commit and send; a transactional outbox would close that gap.
 */
@Component
@RequiredArgsConstructor
class ShipmentEventPublisher {

    private final KafkaTemplate<String, ShipmentEvent> kafkaTemplate;

    @Value("${commerce.topics.shipment-events}")
    private final String topic;

    @TransactionalEventListener
    void publish(ShipmentEvent event) {
        kafkaTemplate.send(topic, event.orderId().toString(), event);
    }
}
