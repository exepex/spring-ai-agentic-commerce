package io.github.exepex.commerce.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

/**
 * Talks to the real server through a real MCP client over streamable HTTP, as the agents do. Postgres and Kafka run
 * in containers; one WireMock server stands in for the four commerce services.
 */
class CommerceMcpServerIntegrationTest extends McpServerTestSupport {

    @Autowired
    private KafkaTemplate<String, String> kafka;

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
        McpSchema.CallToolResult result = call(exceptionsAgent, "propose_order", Map.of("customerEmail", "ada@example.com",
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
    void aRefundWithinTheLimitRunsOnceEvenWhenTheAgentRetriesIt() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        Map<String, Object> refund = Map.of("orderId", orderId.toString(), "amount", 39.50,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId);

        String first = text(call(exceptionsAgent, "issue_refund", refund));
        String second = text(call(exceptionsAgent, "issue_refund", refund));

        assertThat((String) JsonPath.read(first, "$.status")).isEqualTo("EXECUTED");
        assertThat((String) JsonPath.read(second, "$.refundRequestId")).isEqualTo(JsonPath.read(first, "$.refundRequestId"));
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
    }

    @Test
    void aRefundAboveTheLimitWaitsForAHumanAndRunsOnceApproved() {
        UUID orderId = stubOrder("ada@example.com", "129.90");
        stubRefundSucceeds(orderId);

        String result = text(call(exceptionsAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 129.90, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId)));

        assertThat((String) JsonPath.read(result, "$.status")).isEqualTo("PENDING_APPROVAL");
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));

        String approved = rest().post().uri("/api/refund-requests/{id}/approve", (String) JsonPath.read(result, "$.refundRequestId"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("by", "ops@example.com", "note", "Goodwill: our stock error"))
                .retrieve().body(String.class);
        assertThat((String) JsonPath.read(approved, "$.status")).isEqualTo("EXECUTED");
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds"))
                .withRequestBody(matchingJsonPath("$.idempotencyKey", equalTo("refund-" + orderId))));
    }

    @Test
    void splittingARefundDoesNotGetAroundTheApprovalLimit() {
        UUID orderId = stubOrder("ada@example.com", "129.90");
        stubRefundSucceeds(orderId);

        String first = text(call(exceptionsAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 65, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-1")));
        String second = text(call(exceptionsAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 64.90, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-2")));

        assertThat((String) JsonPath.read(first, "$.status")).isEqualTo("EXECUTED");
        assertThat((String) JsonPath.read(second, "$.status")).isEqualTo("PENDING_APPROVAL");
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
    }

    @Test
    void twoRefundsAtOnceCannotTogetherStayUnderTheApprovalLimit() throws Exception {
        UUID orderId = stubOrder("ada@example.com", "129.90");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(okJson("""
                {"id": "%s", "orderId": "%s", "amount": 129.90, "refundedAmount": 0, "refundable": 129.90, "currency": "EUR",
                 "status": "SUCCEEDED", "refunds": []}""".formatted(UUID.randomUUID(), orderId)).withFixedDelay(300)));
        stubRefundSucceeds(orderId);

        runTogether(() -> refundSixty(exceptionsAgent, orderId, "a"), () -> refundSixty(assistant, orderId, "b"));

        String requests = rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class);
        List<String> statuses = JsonPath.read(requests, "$[*].status");
        assertThat(statuses).containsExactlyInAnyOrder("EXECUTED", "PENDING_APPROVAL");
    }

    @Test
    void aRefundReasonTooLongForThePaymentServiceIsRefusedBeforeAnyMoneyMoves() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);

        McpSchema.CallToolResult refused = call(exceptionsAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 39.50, "reason", "x".repeat(501), "idempotencyKey", "refund-" + orderId));

        assertThat(refused.isError()).isTrue();
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
    }

    @Test
    void theShoppingAssistantCannotEscalateAnotherCustomersOrder() {
        UUID orderId = stubOrder("grace@example.com", "39.50");

        McpSchema.CallToolResult refused = call(assistant, "escalate_to_human", Map.of("orderId", orderId.toString(),
                "summary", "please refund this", "customerEmail", "ada@example.com"));

        assertThat(refused.isError()).isTrue();
        assertThat(rest().get().uri("/api/escalations").retrieve().body(String.class)).doesNotContain(orderId.toString());
    }

    @Test
    void theSameRefundSentTwiceAtOnceGivesOneRequestAndBothCallsSucceed() throws Exception {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(okJson("""
                {"id": "%s", "orderId": "%s", "amount": 39.50, "refundedAmount": 0, "refundable": 39.50, "currency": "EUR",
                 "status": "SUCCEEDED", "refunds": []}""".formatted(UUID.randomUUID(), orderId)).withFixedDelay(300)));
        stubRefundSucceeds(orderId);

        List<Integer> succeeded = runTogether(
                () -> refundAll(exceptionsAgent, orderId), () -> refundAll(exceptionsAgent, orderId));

        assertThat(succeeded).containsExactly(1, 1);
        String requests = rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class);
        assertThat((List<?>) JsonPath.read(requests, "$")).hasSize(1);
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
    @SuppressWarnings("unchecked")
    void aResolutionNoteTooLongToStoreLeavesTheEscalationAndTheAuditTrailUnchanged() {
        String escalationId = JsonPath.read(text(call(exceptionsAgent, "escalate_to_human",
                Map.of("summary", "payments keep failing"))), "$.id");

        int status = rest().post().uri("/api/escalations/{id}/resolve", escalationId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("by", "ops@example.com", "note", "resolved ".repeat(112)))
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(422);
        String escalations = rest().get().uri("/api/escalations").retrieve().body(String.class);
        assertThat((List<String>) JsonPath.read(escalations, "$[?(@.id == '" + escalationId + "')].status"))
                .containsExactly("OPEN");
        String audit = rest().get().uri("/api/audit-events").retrieve().body(String.class);
        assertThat(audit).doesNotContain("resolved ".repeat(112));
    }

    @Test
    void anApprovalNoteTooLongToStoreIsRefusedBeforeAnyMoneyMoves() {
        UUID orderId = stubOrder("ada@example.com", "129.90");
        stubRefundSucceeds(orderId);
        String requestId = JsonPath.read(text(call(exceptionsAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 129.90, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId))), "$.refundRequestId");

        int status = rest().post().uri("/api/refund-requests/{id}/approve", requestId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("by", "ops@example.com", "note", "x".repeat(1001)))
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(422);
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
        String requests = rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class);
        assertThat((String) JsonPath.read(requests, "$[0].status")).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void anIdempotencyKeyTooLongToStoreIsRefused() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);

        McpSchema.CallToolResult refused = call(exceptionsAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 39.50, "reason", "item out of stock", "idempotencyKey", "k".repeat(201)));

        assertThat(refused.isError()).isTrue();
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
    }

    @Test
    void twoPeopleDecidingOnTheSameRefundAtOnceCannotBothAct() throws Exception {
        UUID orderId = stubOrder("ada@example.com", "129.90");
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(201)
                .withFixedDelay(500).withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id": "%s", "amount": 129.90, "reason": "item out of stock", "providerReference": "re_test"}"""
                        .formatted(UUID.randomUUID()))));
        String requestId = JsonPath.read(text(call(exceptionsAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 129.90, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId))), "$.refundRequestId");

        List<Integer> statuses = runTogether(
                () -> decide(requestId, "approve"), () -> decide(requestId, "reject"));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        String decided = rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class);
        String status = JsonPath.read(decided, "$[0].status");
        SERVICES.verify(status.equals("EXECUTED") ? 1 : 0, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
        assertThat(status).isIn("EXECUTED", "REJECTED");
    }

    @Test
    void aRefundThatFailedBecausePaymentsWereDownSucceedsWhenRetriedWithTheSameKey() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(aResponse().withStatus(503)));
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(503)));
        Map<String, Object> refund = Map.of("orderId", orderId.toString(), "amount", 39.50,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId);

        String failed = text(call(exceptionsAgent, "issue_refund", refund));
        assertThat((String) JsonPath.read(failed, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(failed, "$.message")).contains("safe");

        stubRefundSucceeds(orderId);
        assertThat((String) JsonPath.read(text(call(exceptionsAgent, "issue_refund", refund)), "$.status")).isEqualTo("EXECUTED");
    }

    @Test
    void anAgentCannotRepeatARefundKeyAnotherAgentAskedFor() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(503)));
        Map<String, Object> stockOutRefund = Map.of("orderId", orderId.toString(), "amount", 39.50,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-stockout");
        call(exceptionsAgent, "issue_refund", stockOutRefund);
        stubRefundSucceeds(orderId);

        McpSchema.CallToolResult reused = call(assistant, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 39.50, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-stockout",
                "customerEmail", "ada@example.com"));

        assertThat(reused.isError()).isTrue();
        assertThat(text(reused)).contains("another agent");
        String requests = rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class);
        assertThat((String) JsonPath.read(requests, "$[0].status")).isEqualTo("FAILED");
    }

    @Test
    void anOrderLookupShowsEachRefundsKeyAndWhenTheCustomerWasNotified() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        call(exceptionsAgent, "issue_refund", Map.of("orderId", orderId.toString(), "amount", 39.50,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-stockout"));
        String before = text(call(exceptionsAgent, "get_order", Map.of("orderId", orderId.toString())));
        call(exceptionsAgent, "notify_customer", Map.of("orderId", orderId.toString(),
                "message", "Sorry, your headlamp is out of stock; your money is refunded."));

        String after = text(call(exceptionsAgent, "get_order", Map.of("orderId", orderId.toString())));

        assertThat((String) JsonPath.read(after, "$.refunds[0].idempotencyKey")).isEqualTo("refund-" + orderId + "-stockout");
        assertThat((Integer) JsonPath.read(before, "$.notifications.length()")).isZero();
        assertThat((String) JsonPath.read(after, "$.notifications[0].sentBy")).isEqualTo("order-exceptions-agent");
    }

    @Test
    void anOrderLookupSaysSoWhenThePaymentServiceIsDownInsteadOfShowingNoPayment() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(aResponse().withStatus(503)));

        String order = text(call(exceptionsAgent, "get_order", Map.of("orderId", orderId.toString())));

        assertThat((String) JsonPath.read(order, "$.payment.status")).isEqualTo("UNKNOWN");
        assertThat((String) JsonPath.read(order, "$.payment.message")).contains("payment service is unavailable");
    }

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

    private static String stockOut(UUID eventId, String reason, UUID... affectedOrderIds) {
        String orderIds = String.join(", ", Arrays.stream(affectedOrderIds).map(id -> "\"" + id + "\"").toList());
        return """
                {"eventId": "%s", "occurredAt": "2026-10-02T09:15:00Z", "productId": "%s", "sku": "RUN-SHOE-BLUE-43",
                 "onHand": 1, "reserved": 2, "shortfall": 1, "reason": "%s", "affectedOrderIds": [%s]}"""
                .formatted(eventId, UUID.randomUUID(), reason, orderIds);
    }

    private String timeline(UUID orderId) {
        return rest().get().uri("/api/orders/{orderId}/timeline", orderId).retrieve().body(String.class);
    }

    /** Asks for the full €39.50 refund with one fixed key and returns 1 when the call succeeded. */
    private static int refundAll(McpSyncClient client, UUID orderId) {
        McpSchema.CallToolResult result = call(client, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 39.50, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId));
        return Boolean.TRUE.equals(result.isError()) ? 0 : 1;
    }

    /** Asks for a €60 refund and returns 1 when the tool call succeeded, 0 when it was refused. */
    private static int refundSixty(McpSyncClient client, UUID orderId, String key) {
        McpSchema.CallToolResult result = call(client, "issue_refund", Map.of("orderId", orderId.toString(), "amount", 60,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-" + key,
                "customerEmail", "ada@example.com"));
        return Boolean.TRUE.equals(result.isError()) ? 0 : 1;
    }

    private int decide(String requestId, String decision) {
        return rest().post().uri("/api/refund-requests/{id}/" + decision, requestId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("by", "ops@example.com"))
                .exchange((request, response) -> response.getStatusCode().value());
    }
}
