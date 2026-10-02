package io.github.exepex.commerce.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends order events to Kafka after the order change has committed. As in the catalog, an event can be lost if the
 * process dies between commit and send; a transactional outbox would close that gap.
 */
@Component
class OrderEventPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final String topic;

    OrderEventPublisher(KafkaTemplate<String, OrderEvent> kafkaTemplate,
            @Value("${commerce.topics.order-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener
    void publish(OrderEvent event) {
        kafkaTemplate.send(topic, event.orderId().toString(), event);
    }
}
