package io.github.exepex.commerce.agent;

import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Wakes the order-exceptions agent for every order a stock-out event names. */
@Component
class StockOutListener {

    private final OrderExceptionsAgent agent;
    private final JsonMapper jsonMapper;

    StockOutListener(OrderExceptionsAgent agent, JsonMapper jsonMapper) {
        this.agent = agent;
        this.jsonMapper = jsonMapper;
    }

    /**
     * A stock-out whose order could not be handed to anyone is delivered again: every 15 seconds for five minutes,
     * long enough to ride out a restart of the MCP server.
     */
    @Bean
    static DefaultErrorHandler stockOutRetries() {
        return new DefaultErrorHandler(new FixedBackOff(Duration.ofSeconds(15).toMillis(), 20));
    }

    @KafkaListener(topics = "${commerce.topics.stock-out}")
    void onStockOut(String json) {
        JsonNode event = jsonMapper.readTree(json);
        for (JsonNode orderId : event.path("affectedOrderIds")) {
            agent.handleStockOut(UUID.fromString(orderId.asString()), json);
        }
    }
}
