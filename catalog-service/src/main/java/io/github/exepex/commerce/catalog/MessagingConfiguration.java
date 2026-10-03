package io.github.exepex.commerce.catalog;

import io.github.exepex.commerce.platform.events.EventRoute;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The events the catalog announces on Kafka once the stock change behind them has committed. */
@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

    /** Keyed by product, so one product's stock-outs are read in the order they happened. */
    @Bean
    EventRoute<StockOutEvent> stockOutRoute(CatalogTopics topics) {
        return EventRoute.of(StockOutEvent.class, topics.stockOut(), event -> event.productId().toString());
    }
}
