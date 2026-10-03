package io.github.exepex.commerce.platform.events;

import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the events services raise to Kafka once the change behind them has committed, so consumers never see a change
 * that was rolled back. An event can be lost if the process dies between commit and send; a transactional outbox would
 * close that gap. Events without a route are not announced.
 */
public class AfterCommitEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final List<EventRoute<?>> routes;

    public AfterCommitEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, List<EventRoute<?>> routes) {
        this.kafkaTemplate = kafkaTemplate;
        this.routes = List.copyOf(routes);
    }

    @TransactionalEventListener
    public void publish(Object event) {
        for (var route : routes) {
            if (route.carries(event)) {
                kafkaTemplate.send(route.topic(), route.keyOf(event), event);
            }
        }
    }
}
