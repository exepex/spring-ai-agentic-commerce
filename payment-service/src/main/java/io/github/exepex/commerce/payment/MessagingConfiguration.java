package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.constants.ConfigKeys;
import io.github.exepex.commerce.platform.events.EventRoute;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The events the payment service announces on Kafka once the change behind them has committed. */
@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

    /** Keyed by order, so one order's payment events are read in the order they happened. */
    @Bean
    EventRoute<RefundFailedEvent> refundFailedRoute(@Value(ConfigKeys.PAYMENT_EVENTS_TOPIC) String topic) {
        return EventRoute.of(RefundFailedEvent.class, topic, event -> event.orderId().toString());
    }
}
