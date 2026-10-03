package io.github.exepex.commerce.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Talks to the real server through a real MCP client over streamable HTTP, as the agents do: who may call which tool,
 * customer scoping, and what the shop's tools answer. Postgres and Kafka run in containers; one WireMock server stands
 * in for the four commerce services.
 */
class CommerceMcpServerIntegrationTest extends McpServerTestSupport {

    @Test
    void refusesACallerWithoutAnAgentToken() {
        HttpStatusCode status = RestClient.create("http://localhost:" + port).post().uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON).body("{}")
                .exchange((request, response) -> response.getStatusCode());
        assertThat(status.value()).isEqualTo(401);
    }

    @Test
    void offersTheShopTools() {
        assertThat(assistant.listTools().tools()).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder(
                "search_products", "find_customer_orders", "get_order", "track_shipment", "propose_order",
                "cancel_order", "issue_refund", "notify_customer", "escalate_to_human");
    }

    @Test
    void anAgentCannotCallAToolItIsNotPermittedToUse() {
        McpSchema.CallToolResult result = call(incidentAgent, "propose_order", Map.of("customerEmail", "ada@example.com",
                "lines", List.of(Map.of("productId", UUID.randomUUID().toString(), "quantity", 1))));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("not permitted to call propose_order");
        assertThat(rest().get().uri("/api/audit-events").retrieve().body(String.class))
                .contains("\"outcome\":\"DENIED\"");
    }

    @Test
    void theShoppingAssistantCannotSeeAnotherCustomersOrder() {
        UUID graceOrder = stubOrder("grace@example.com", "39.50");

        McpSchema.CallToolResult result = call(assistant, "get_order",
                Map.of("orderId", graceOrder.toString(), "customerEmail", "ada@example.com"));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("does not belong to the customer in this conversation");
    }

    @Test
    void theShoppingAssistantCannotEscalateAnotherCustomersOrder() {
        UUID orderId = stubOrder("grace@example.com", "39.50");

        McpSchema.CallToolResult refused = call(assistant, "escalate_to_human", Map.of("orderId", orderId.toString(),
                "summary", "please refund this", "customerEmail", "ada@example.com"));

        assertThat(refused.isError()).isTrue();
        assertThat(rest().get().uri("/api/cases?orderId={id}", orderId).retrieve().body(String.class)).isEqualTo("[]");
    }

    @Test
    void aProposalCannotListTheSameProductTwice() {
        UUID productId = UUID.randomUUID();
        SERVICES.stubFor(get("/api/products").willReturn(okJson("""
                [{"id": "%s", "sku": "HEADLAMP-400", "name": "Headlamp", "description": "", "price": 39.50,
                  "currency": "EUR", "onHand": 5, "reserved": 0, "available": 5}]""".formatted(productId))));

        McpSchema.CallToolResult refused = call(assistant, "propose_order", Map.of("customerEmail", "ada@example.com",
                "lines", List.of(Map.of("productId", productId.toString(), "quantity", 1),
                        Map.of("productId", productId.toString(), "quantity", 2))));

        assertThat(refused.isError()).isTrue();
        assertThat(text(refused)).contains("only one line");
    }

    @Test
    void anOrderLookupShowsEachRefundsKeyAndWhenTheCustomerWasNotified() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        call(incidentAgent, "issue_refund", Map.of("orderId", orderId.toString(), "amount", 39.50,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-stockout"));
        String before = text(call(incidentAgent, "get_order", Map.of("orderId", orderId.toString())));
        call(incidentAgent, "notify_customer", Map.of("orderId", orderId.toString(),
                "message", "Sorry, your headlamp is out of stock; your money is refunded."));

        String after = text(call(incidentAgent, "get_order", Map.of("orderId", orderId.toString())));

        assertThat((String) JsonPath.read(after, "$.refunds[0].idempotencyKey")).isEqualTo("refund-" + orderId + "-stockout");
        assertThat((Integer) JsonPath.read(before, "$.notifications.length()")).isZero();
        assertThat((String) JsonPath.read(after, "$.notifications[0].sentBy")).isEqualTo("incident-agent");
    }

    @Test
    void aMessageWithAKeyIsSentOnceHoweverOftenAndAtOnceItIsAskedFor() throws Exception {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        Map<String, Object> message = Map.of("orderId", orderId.toString(), "message", "Sorry, it is out of stock.",
                "idempotencyKey", "notify-" + orderId + "-INC0010001");

        runTogether(() -> text(call(incidentAgent, "notify_customer", message)).length(),
                () -> text(call(incidentAgent, "notify_customer", message)).length());
        String again = text(call(incidentAgent, "notify_customer", message));

        assertThat(again).contains("already told");
        String notifications = rest().get().uri("/api/notifications?orderId={id}", orderId).retrieve().body(String.class);
        assertThat((Integer) JsonPath.read(notifications, "$.length()")).isEqualTo(1);
        List<String> summaries = JsonPath.read(timeline(orderId), "$[?(@.action == 'notify_customer')].summary");
        assertThat(summaries).hasSize(3).filteredOn(summary -> summary.startsWith("Already notified")).hasSize(2);
    }

    @Test
    void anOrderLookupSaysSoWhenThePaymentServiceIsDownInsteadOfShowingNoPayment() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(aResponse().withStatus(503)));

        String order = text(call(incidentAgent, "get_order", Map.of("orderId", orderId.toString())));

        assertThat((String) JsonPath.read(order, "$.payment.status")).isEqualTo("UNKNOWN");
        assertThat((String) JsonPath.read(order, "$.payment.message")).contains("payment service is unavailable");
    }

    @Test
    void trackingAShipmentShowsWhatTheCarrierReported() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/shipments/" + orderId).willReturn(okJson("""
                {"id": "%s", "orderId": "%s", "trackingNumber": "ACTEST000002", "status": "DELIVERY_FAILED",
                 "estimatedDelivery": "2026-10-05", "createdAt": "2026-10-02T10:00:00Z",
                 "shippedAt": "2026-10-02T12:00:00Z", "deliveredAt": null,
                 "deliveryProblem": "Address not found", "cancelledAt": null}""".formatted(UUID.randomUUID(), orderId))));

        String shipment = text(call(assistant, "track_shipment",
                Map.of("orderId", orderId.toString(), "customerEmail", "ada@example.com")));

        assertThat((String) JsonPath.read(shipment, "$.status")).isEqualTo("DELIVERY_FAILED");
        assertThat((String) JsonPath.read(shipment, "$.shippedAt")).isEqualTo("2026-10-02T12:00:00Z");
        assertThat((String) JsonPath.read(shipment, "$.deliveryProblem")).isEqualTo("Address not found");
    }
}
