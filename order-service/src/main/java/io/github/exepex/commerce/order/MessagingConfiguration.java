package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.constants.ConfigKeys;
import io.github.exepex.commerce.platform.events.EventRoute;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The events the order service announces on Kafka once the order change behind them has committed. */
@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

    /** Keyed by order, so one order's events are read in the order they happened. */
    @Bean
    EventRoute<OrderEvent> orderEventRoute(@Value(ConfigKeys.ORDER_EVENTS_TOPIC) String topic) {
        return EventRoute.of(OrderEvent.class, topic, event -> event.orderId().toString());
    }
}
