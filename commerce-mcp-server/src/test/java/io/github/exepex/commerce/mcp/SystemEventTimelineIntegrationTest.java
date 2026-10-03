package io.github.exepex.commerce.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * What the commerce services announce on Kafka appears on the order's timeline once, at the time it happened, however
 * often it is delivered.
 */
class SystemEventTimelineIntegrationTest extends McpServerTestSupport {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Test
    void aStockOutAppearsOnceOnTheTimelineOfEveryAffectedOrderAtTheTimeItHappenedEvenWhenDeliveredAgain() {
        UUID orderId = UUID.randomUUID();
        UUID otherOrderId = UUID.randomUUID();
        String stockOut = stockOut(UUID.randomUUID(), "damaged in warehouse", orderId, otherOrderId);

        kafka.send("inventory.stock-out", "product", stockOut).join();
        kafka.send("inventory.stock-out", "product", stockOut).join();
        kafka.send("inventory.stock-out", "product", stockOut(UUID.randomUUID(), "lost in transit", orderId)).join();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(timeline(orderId)).contains("lost in transit"));
        List<String> damaged = JsonPath.read(timeline(orderId), "$[?(@.summary =~ /.*damaged in warehouse.*/)].occurredAt");
        assertThat(damaged).containsExactly("2026-10-02T09:15:00Z");
        assertThat(timeline(otherOrderId)).contains("STOCK_OUT", "damaged in warehouse");
    }

    @Test
    void anOrderEventDeliveredAgainAppearsOnceOnTheTimeline() {
        UUID orderId = UUID.randomUUID();
        String confirmed = """
                {"eventId": "%s", "type": "ORDER_CONFIRMED", "orderId": "%s", "customerEmail": "ada@example.com",
                 "occurredAt": "2026-10-02T09:10:00Z"}""".formatted(UUID.randomUUID(), orderId);

        kafka.send("order.events", orderId.toString(), confirmed).join();
        kafka.send("order.events", orderId.toString(), confirmed).join();
        kafka.send("order.events", orderId.toString(), """
                {"eventId": "%s", "type": "ORDER_CANCELLED", "orderId": "%s", "customerEmail": "ada@example.com",
                 "occurredAt": "2026-10-02T09:20:00Z"}""".formatted(UUID.randomUUID(), orderId)).join();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(timeline(orderId)).contains("ORDER_CANCELLED"));
        List<String> actions = JsonPath.read(timeline(orderId), "$[*].action");
        assertThat(actions).containsExactly("ORDER_CONFIRMED", "ORDER_CANCELLED");
    }

    @Test
    void theOrderTimelineShowsTheShipmentAndWhatTheCarrierReportedOnce() {
        UUID orderId = UUID.randomUUID();
        String failed = """
                {"eventId": "%s", "type": "SHIPMENT_DELIVERY_FAILED", "orderId": "%s", "customerEmail": "ada@example.com",
                 "trackingNumber": "ACTEST000001", "deliveryProblem": "Nobody home, parcel returned",
                 "occurredAt": "2026-10-02T11:00:00Z"}""".formatted(UUID.randomUUID(), orderId);

        kafka.send("order.events", orderId.toString(), """
                {"eventId": "%s", "type": "ORDER_SHIPPED", "orderId": "%s", "customerEmail": "ada@example.com",
                 "occurredAt": "2026-10-02T10:00:00Z"}""".formatted(UUID.randomUUID(), orderId)).join();
        kafka.send("shipment.events", orderId.toString(), failed).join();
        kafka.send("shipment.events", orderId.toString(), failed).join();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(timeline(orderId)).contains("SHIPMENT_DELIVERY_FAILED", "raise_case"));
        List<String> actions = JsonPath.read(timeline(orderId), "$[*].action");
        assertThat(actions).containsExactly("ORDER_SHIPPED", "SHIPMENT_DELIVERY_FAILED", "raise_case");
        assertThat(timeline(orderId)).contains("Parcel ACTEST000001 could not be delivered: Nobody home, parcel returned");
    }

    private static String stockOut(UUID eventId, String reason, UUID... affectedOrderIds) {
        String orderIds = String.join(", ", Arrays.stream(affectedOrderIds).map(id -> "\"" + id + "\"").toList());
        return """
                {"eventId": "%s", "occurredAt": "2026-10-02T09:15:00Z", "productId": "%s", "sku": "RUN-SHOE-BLUE-43",
                 "onHand": 1, "reserved": 2, "shortfall": 1, "reason": "%s", "affectedOrderIds": [%s]}"""
                .formatted(eventId, UUID.randomUUID(), reason, orderIds);
    }
}
