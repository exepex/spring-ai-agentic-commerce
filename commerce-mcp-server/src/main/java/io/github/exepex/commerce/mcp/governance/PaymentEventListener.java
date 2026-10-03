package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.constants.ConfigKeys;
import io.github.exepex.commerce.mcp.constants.EventFields;
import io.github.exepex.commerce.mcp.constants.EventTypes;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Acts on what the payment service announces on Kafka: a refund that failed after the processor accepted it. */
@Component
@RequiredArgsConstructor
class PaymentEventListener {

    private final RefundService refunds;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = ConfigKeys.PAYMENT_EVENTS_TOPIC)
    void onPaymentEvent(String json) {
        var event = jsonMapper.readTree(json);
        if (EventTypes.REFUND_FAILED.equals(event.path(EventFields.TYPE).asString())) {
            refunds.recordFailedAtProcessor(UUID.fromString(event.path(EventFields.EVENT_ID).asString()),
                    UUID.fromString(event.path(EventFields.ORDER_ID).asString()),
                    event.path(EventFields.IDEMPOTENCY_KEY).asString(), event.path(EventFields.AMOUNT).decimalValue(),
                    event.path(EventFields.CURRENCY).asString());
        }
    }
}
