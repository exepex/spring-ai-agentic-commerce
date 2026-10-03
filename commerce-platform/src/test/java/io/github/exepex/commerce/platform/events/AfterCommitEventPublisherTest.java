package io.github.exepex.commerce.platform.events;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class AfterCommitEventPublisherTest {

    record OrderPlaced(UUID orderId) {}

    record SomethingElse(String name) {}

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);

    private final AfterCommitEventPublisher publisher = new AfterCommitEventPublisher(kafka,
            List.of(EventRoute.of(OrderPlaced.class, "order.events", event -> event.orderId().toString())));

    @Test
    void anEventGoesToItsTopicKeyedAsItsRouteSays() {
        var orderId = UUID.randomUUID();
        var event = new OrderPlaced(orderId);

        publisher.publish(event);

        verify(kafka).send("order.events", orderId.toString(), event);
    }

    @Test
    void anEventWithoutARouteIsNotAnnounced() {
        publisher.publish(new SomethingElse("internal"));

        verifyNoInteractions(kafka);
    }
}
