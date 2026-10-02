package io.github.exepex.commerce.shipping;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends shipment events to Kafka after the change has committed. As in the other services, an event can be lost if
 * the process dies between commit and send; a transactional outbox would close that gap.
 */
@Component
class ShipmentEventPublisher {

    private final KafkaTemplate<String, ShipmentEvent> kafkaTemplate;
    private final String topic;

    ShipmentEventPublisher(KafkaTemplate<String, ShipmentEvent> kafkaTemplate,
            @Value("${commerce.topics.shipment-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener
    void publish(ShipmentEvent event) {
        kafkaTemplate.send(topic, event.orderId().toString(), event);
    }
}
