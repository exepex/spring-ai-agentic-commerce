package io.github.exepex.commerce.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends payment events to Kafka after the change behind them has committed. As in the other services, an event can be
 * lost if the process dies between commit and send; a transactional outbox would close that gap.
 */
@Component
@RequiredArgsConstructor
class PaymentEventPublisher {

    private final KafkaTemplate<String, RefundFailedEvent> kafkaTemplate;

    @Value("${commerce.topics.payment-events}")
    private final String topic;

    @TransactionalEventListener
    void publish(RefundFailedEvent event) {
        kafkaTemplate.send(topic, event.orderId().toString(), event);
    }
}
