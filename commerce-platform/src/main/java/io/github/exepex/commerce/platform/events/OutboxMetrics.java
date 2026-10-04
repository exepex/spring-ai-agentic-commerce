package io.github.exepex.commerce.platform.events;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;

/**
 * The outbox's backlog as metrics: an alert on the oldest event's age catches Kafka being down or the relay being
 * stuck long before customers notice missing updates.
 */
@RequiredArgsConstructor
class OutboxMetrics implements MeterBinder {

    private final OutboxStore outbox;
    private final OutboxProperties properties;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("commerce.outbox.waiting", outbox, store -> store.backlog().waiting())
                .description("Events written but not yet on Kafka")
                .tag("table", properties.table())
                .register(registry);
        Gauge.builder("commerce.outbox.oldest.age", outbox, store -> store.backlog().oldestAgeSeconds())
                .description("How long the oldest event not yet on Kafka has waited")
                .baseUnit("seconds")
                .tag("table", properties.table())
                .register(registry);
    }
}
