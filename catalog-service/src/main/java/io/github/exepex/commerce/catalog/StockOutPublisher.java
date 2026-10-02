package io.github.exepex.commerce.catalog;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends stock-out events to Kafka once the stock change that caused them has committed.
 *
 * <p>Publishing after commit means a consumer never sees an event for a change that rolled back. The trade-off: if
 * the process dies between commit and send, the event is lost. A transactional outbox closes that gap; it is left
 * out to keep this demo small.
 */
@Component
class StockOutPublisher {

    private final KafkaTemplate<String, StockOutEvent> kafkaTemplate;
    private final CatalogTopics topics;

    StockOutPublisher(KafkaTemplate<String, StockOutEvent> kafkaTemplate, CatalogTopics topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @TransactionalEventListener
    void publish(StockOutEvent event) {
        kafkaTemplate.send(topics.stockOut(), event.productId().toString(), event);
    }
}
