package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.cases.CaseService;
import io.github.exepex.commerce.mcp.cases.CaseType;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adds what the commerce services announce on Kafka to the audit trail, so an order's timeline shows the system's
 * steps next to the agents' and the humans', and opens a case for each problem that needs handling: a stock-out, a
 * delivery that failed, a lost parcel. Each event is recorded, and raises its case, once per order, however often
 * Kafka delivers it. Events are read as plain JSON: the topics are contracts, not shared Java types.
 */
@Component
class SystemEventRecorder {

    private final AuditTrail audit;
    private final CaseService cases;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    SystemEventRecorder(AuditTrail audit, CaseService cases, JsonMapper jsonMapper, Clock clock) {
        this.audit = audit;
        this.cases = cases;
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
            case "ORDER_SHIPPED" -> "Order shipped: its stock left the warehouse and the parcel is with the carrier";
            default -> "Order event " + type;
        };
        audit.recordSystemEvent(eventId(event), occurredAt(event), UUID.fromString(event.path("orderId").asString()),
                "order-service", type, summary, null);
    }

    @KafkaListener(topics = "${commerce.topics.shipment-events}")
    void onShipmentEvent(String json) {
        JsonNode event = jsonMapper.readTree(json);
        String type = event.path("type").asString();
        String parcel = "Parcel " + event.path("trackingNumber").asString();
        String problem = event.path("deliveryProblem").asString("");
        String summary = switch (type) {
            case "SHIPMENT_DELIVERED" -> parcel + " delivered to the customer";
            case "SHIPMENT_DELIVERY_FAILED" -> parcel + " could not be delivered: " + problem;
            case "SHIPMENT_LOST" -> parcel + " lost by the carrier: " + problem;
            default -> "Shipment event " + type;
        };
        UUID orderId = UUID.fromString(event.path("orderId").asString());
        audit.recordSystemEvent(eventId(event), occurredAt(event), orderId, "shipping-service", type, summary, null);
        CaseType caseType = switch (type) {
            case "SHIPMENT_DELIVERY_FAILED" -> CaseType.DELIVERY_FAILED;
            case "SHIPMENT_LOST" -> CaseType.PARCEL_LOST;
            default -> null;
        };
        if (caseType != null) {
            cases.raiseFor(eventId(event), caseType, orderId, summary + ". The customer did not receive order "
                    + orderId + ".", "shipping-service");
        }
    }

    @KafkaListener(topics = "${commerce.topics.stock-out}")
    void onStockOut(String json) {
        JsonNode event = jsonMapper.readTree(json);
        String summary = "Stock-out on " + event.path("sku").asString() + ": " + event.path("onHand").asInt()
                + " on hand for " + event.path("reserved").asInt() + " reserved (" + event.path("reason").asString()
                + "). This order can no longer be fulfilled as placed.";
        for (JsonNode affected : event.path("affectedOrderIds")) {
            UUID orderId = UUID.fromString(affected.asString());
            audit.recordSystemEvent(eventId(event), occurredAt(event), orderId, "catalog-service", "STOCK_OUT", summary,
                    json);
            cases.raiseFor(eventId(event), CaseType.STOCK_OUT, orderId, summary, "catalog-service");
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
