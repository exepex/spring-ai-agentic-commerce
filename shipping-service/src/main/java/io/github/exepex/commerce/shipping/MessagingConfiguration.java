package io.github.exepex.commerce.shipping;

import io.github.exepex.commerce.platform.events.EventRoute;
import io.github.exepex.commerce.shipping.constants.ConfigKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The events the shipping service announces on Kafka once the carrier's report behind them has committed. */
@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

    /** Keyed by order, so one order's shipment events are read in the order they happened. */
    @Bean
    EventRoute<ShipmentEvent> shipmentEventRoute(@Value(ConfigKeys.SHIPMENT_EVENTS_TOPIC) String topic) {
        return EventRoute.of(ShipmentEvent.class, topic, event -> event.orderId().toString());
    }
}
