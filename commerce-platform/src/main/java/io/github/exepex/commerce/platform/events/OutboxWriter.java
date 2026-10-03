package io.github.exepex.commerce.platform.events;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes the events a service raises into its outbox, in the transaction of the change they announce: an event exists
 * exactly when its change committed, even if the process dies right after, and the relay puts it on Kafka. Events are
 * written as the service's Kafka serializer writes them, so the JSON on the topics stays the same. Events without a
 * route are not announced.
 */
class OutboxWriter {

    private final OutboxStore outbox;
    private final List<EventRoute<?>> routes;
    private final Serializer<Object> serializer;
    private final TracePropagation tracing;
    private final OutboxRelay relay;

    OutboxWriter(OutboxStore outbox, List<EventRoute<?>> routes, Serializer<Object> serializer,
            TracePropagation tracing, OutboxRelay relay) {
        this.outbox = outbox;
        this.routes = List.copyOf(routes);
        this.serializer = serializer;
        this.tracing = tracing;
        this.relay = relay;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void write(Object event) {
        for (var route : routes) {
            if (route.carries(event)) {
                var json = serializer.serialize(route.topic(), new RecordHeaders(), event);
                outbox.add(route.topic(), route.keyOf(event), new String(json, StandardCharsets.UTF_8),
                        TraceHeaders.encode(tracing.currentTrace()));
            }
        }
    }

    /** Sends the events now rather than at the relay's next poll. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void wakeRelay(Object event) {
        if (routes.stream().anyMatch(route -> route.carries(event))) {
            relay.wakeUp();
        }
    }
}
