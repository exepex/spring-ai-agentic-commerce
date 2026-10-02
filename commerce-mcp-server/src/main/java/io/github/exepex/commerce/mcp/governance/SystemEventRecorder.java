package io.github.exepex.commerce.mcp.governance;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adds what the commerce services announce on Kafka to the audit trail, so an order's timeline shows the system's
 * steps next to the agents' and the humans'. Each event is recorded once, however often Kafka delivers it. Events are
 * read as plain JSON: the topics are contracts, not shared Java types.
 */
@Component
class SystemEventRecorder {

    private final AuditTrail audit;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    SystemEventRecorder(AuditTrail audit, JsonMapper jsonMapper, Clock clock) {
        this.audit = audit;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @KafkaListener(topics = "${commerce.topics.order-events}")
    void onOrderEvent(String json) {
        JsonNode event = jsonMapper.readTree(json);
        String type = event.path("type").asString();
        String summary = switch (type) {
            case "ORDER_CONFIRMED" -> "Order confirmed and paid; shipping notified";
            case "ORDER_CANCELLED" -> "Order cancelled; stock released and shipment cancelled";
            default -> "Order event " + type;
        };
        audit.recordSystemEvent(eventId(event), occurredAt(event), UUID.fromString(event.path("orderId").asString()),
                "order-service", type, summary, null);
    }

    @KafkaListener(topics = "${commerce.topics.stock-out}")
    void onStockOut(String json) {
        JsonNode event = jsonMapper.readTree(json);
        String summary = "Stock-out on " + event.path("sku").asString() + ": " + event.path("onHand").asInt()
                + " on hand for " + event.path("reserved").asInt() + " reserved (" + event.path("reason").asString()
                + "). This order can no longer be fulfilled as placed.";
        for (JsonNode orderId : event.path("affectedOrderIds")) {
            audit.recordSystemEvent(eventId(event), occurredAt(event), UUID.fromString(orderId.asString()),
                    "catalog-service", "STOCK_OUT", summary, json);
        }
    }

    private static UUID eventId(JsonNode event) {
        return UUID.fromString(event.path("eventId").asString());
    }

    /** When the service says it happened: a consumer that catches up late must not reorder the timeline. */
    private Instant occurredAt(JsonNode event) {
        String occurredAt = event.path("occurredAt").asString("");
        return occurredAt.isEmpty() ? Instant.now(clock) : Instant.parse(occurredAt);
    }
}
