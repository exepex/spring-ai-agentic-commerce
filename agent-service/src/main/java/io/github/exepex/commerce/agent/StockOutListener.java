package io.github.exepex.commerce.agent;

import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
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

    @KafkaListener(topics = "${commerce.topics.stock-out}")
    void onStockOut(String json) {
        JsonNode event = jsonMapper.readTree(json);
        for (JsonNode orderId : event.path("affectedOrderIds")) {
            agent.handleStockOut(UUID.fromString(orderId.asString()), json);
        }
    }
}
