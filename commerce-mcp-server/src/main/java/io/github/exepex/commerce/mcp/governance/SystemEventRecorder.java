package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.cases.CaseService;
import io.github.exepex.commerce.mcp.cases.CaseType;
import io.github.exepex.commerce.mcp.constants.Actors;
import io.github.exepex.commerce.mcp.constants.AuditSummaries;
import io.github.exepex.commerce.mcp.constants.ConfigKeys;
import io.github.exepex.commerce.mcp.constants.EventFields;
import io.github.exepex.commerce.mcp.constants.EventTypes;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
class SystemEventRecorder {

    private final AuditTrail audit;
    private final CaseService cases;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @KafkaListener(topics = ConfigKeys.ORDER_EVENTS_TOPIC)
    void onOrderEvent(String json) {
        var event = jsonMapper.readTree(json);
        var type = event.path(EventFields.TYPE).asString();
        var summary = switch (type) {
            case EventTypes.ORDER_CONFIRMED -> AuditSummaries.ORDER_CONFIRMED;
            case EventTypes.ORDER_CANCELLED -> AuditSummaries.ORDER_CANCELLED;
            case EventTypes.ORDER_SHIPPED -> AuditSummaries.ORDER_SHIPPED;
            default -> AuditSummaries.OTHER_ORDER_EVENT.formatted(type);
        };
        audit.recordSystemEvent(eventId(event), occurredAt(event),
                UUID.fromString(event.path(EventFields.ORDER_ID).asString()), Actors.ORDER_SERVICE, type, summary, null);
    }

    @KafkaListener(topics = ConfigKeys.SHIPMENT_EVENTS_TOPIC)
    void onShipmentEvent(String json) {
        var event = jsonMapper.readTree(json);
        var type = event.path(EventFields.TYPE).asString();
        var parcel = AuditSummaries.PARCEL.formatted(event.path(EventFields.TRACKING_NUMBER).asString());
        var problem = event.path(EventFields.DELIVERY_PROBLEM).asString(EventFields.ABSENT);
        var summary = switch (type) {
            case EventTypes.SHIPMENT_DELIVERED -> AuditSummaries.PARCEL_DELIVERED.formatted(parcel);
            case EventTypes.SHIPMENT_DELIVERY_FAILED -> AuditSummaries.PARCEL_NOT_DELIVERED.formatted(parcel, problem);
            case EventTypes.SHIPMENT_LOST -> AuditSummaries.PARCEL_LOST.formatted(parcel, problem);
            default -> AuditSummaries.OTHER_SHIPMENT_EVENT.formatted(type);
        };
        var orderId = UUID.fromString(event.path(EventFields.ORDER_ID).asString());
        audit.recordSystemEvent(eventId(event), occurredAt(event), orderId, Actors.SHIPPING_SERVICE, type, summary,
                null);
        var caseType = switch (type) {
            case EventTypes.SHIPMENT_DELIVERY_FAILED -> CaseType.DELIVERY_FAILED;
            case EventTypes.SHIPMENT_LOST -> CaseType.PARCEL_LOST;
            default -> null;
        };
        if (caseType != null) {
            cases.raiseFor(eventId(event), caseType, orderId,
                    AuditSummaries.PARCEL_NOT_RECEIVED.formatted(summary, orderId), Actors.SHIPPING_SERVICE);
        }
    }

    @KafkaListener(topics = ConfigKeys.STOCK_OUT_TOPIC)
    void onStockOut(String json) {
        var event = jsonMapper.readTree(json);
        var summary = AuditSummaries.STOCK_OUT.formatted(event.path(EventFields.SKU).asString(),
                event.path(EventFields.ON_HAND).asInt(), event.path(EventFields.RESERVED).asInt(),
                event.path(EventFields.REASON).asString());
        for (var affected : event.path(EventFields.AFFECTED_ORDER_IDS)) {
            var orderId = UUID.fromString(affected.asString());
            audit.recordSystemEvent(eventId(event), occurredAt(event), orderId, Actors.CATALOG_SERVICE,
                    EventTypes.STOCK_OUT, summary, json);
            cases.raiseFor(eventId(event), CaseType.STOCK_OUT, orderId, summary, Actors.CATALOG_SERVICE);
        }
    }

    private static UUID eventId(JsonNode event) {
        return UUID.fromString(event.path(EventFields.EVENT_ID).asString());
    }

    /** When the service says it happened: a consumer that catches up late must not reorder the timeline. */
    private Instant occurredAt(JsonNode event) {
        var occurredAt = event.path(EventFields.OCCURRED_AT).asString(EventFields.ABSENT);
        return occurredAt.isEmpty() ? Instant.now(clock) : Instant.parse(occurredAt);
    }
}
