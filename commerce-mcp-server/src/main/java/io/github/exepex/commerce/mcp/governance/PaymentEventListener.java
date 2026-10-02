package io.github.exepex.commerce.mcp.governance;

import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Acts on what the payment service announces on Kafka: a refund that failed after the processor accepted it. */
@Component
class PaymentEventListener {

    private final RefundService refunds;
    private final JsonMapper jsonMapper;

    PaymentEventListener(RefundService refunds, JsonMapper jsonMapper) {
        this.refunds = refunds;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${commerce.topics.payment-events}")
    void onPaymentEvent(String json) {
        JsonNode event = jsonMapper.readTree(json);
        if ("REFUND_FAILED".equals(event.path("type").asString())) {
            refunds.recordFailedAtProcessor(UUID.fromString(event.path("eventId").asString()),
                    UUID.fromString(event.path("orderId").asString()),
                    event.path("idempotencyKey").asString(), event.path("amount").decimalValue(),
                    event.path("currency").asString());
        }
    }
}
