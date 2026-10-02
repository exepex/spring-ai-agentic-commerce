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
     * A stock-out or incident that could not be handed to anyone is delivered again every 15 seconds until the
     * hand-off succeeds, so no work is dropped however long an MCP server is down. Any other failure is not retried,
     * so a malformed event cannot block the ones behind it.
     */
    @Bean
    static DefaultErrorHandler stockOutRetries() {
        DefaultErrorHandler retries = new DefaultErrorHandler(
                new FixedBackOff(Duration.ofSeconds(15).toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
        retries.defaultFalse();
        retries.addRetryableExceptions(HandOffFailedException.class);
        return retries;
    }

    @KafkaListener(topics = "${commerce.topics.stock-out}")
    void onStockOut(String json) {
        JsonNode event = jsonMapper.readTree(json);
        for (JsonNode orderId : event.path("affectedOrderIds")) {
            agent.handleStockOut(UUID.fromString(orderId.asString()), json);
        }
    }
}
