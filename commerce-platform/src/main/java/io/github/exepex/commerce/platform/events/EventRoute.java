package io.github.exepex.commerce.platform.events;

import java.util.function.Function;

/**
 * Where an event a service raises goes once the change behind it has committed: the Kafka topic, and the key that
 * keeps one order's or product's events in sequence. A service declares one route per event it announces.
 *
 * @param <E> the event
 */
public record EventRoute<E>(Class<E> type, String topic, Function<E, String> key) {

    public static <E> EventRoute<E> of(Class<E> type, String topic, Function<E, String> key) {
        return new EventRoute<>(type, topic, key);
    }

    boolean carries(Object event) {
        return type.isInstance(event);
    }

    String keyOf(Object event) {
        return key.apply(type.cast(event));
    }
}
