package io.github.exepex.commerce.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.MediaType;

/**
 * An escalation is worked by exactly one person: open, then assigned to whoever took it, then resolved by them. Only
 * the assignee can resolve it, hand it back, or retry the refund of the escalated order.
 */
class EscalationLifecycleIntegrationTest extends McpServerTestSupport {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    private static final String ANA = "ana@trailhead.example";
    private static final String BEN = "ben@trailhead.example";

    @Test
    void twoPeopleTakingTheSameEscalationAtOnceOnlyOneGetsIt() throws Exception {
        String escalationId = escalate(null);

        List<Integer> statuses = runTogether(() -> act(escalationId, "assign", ANA), () -> act(escalationId, "assign", BEN));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        String assignee = JsonPath.read(escalation(escalationId), "$.assignedTo");
        assertThat(assignee).isIn(ANA, BEN);
        assertThat((String) JsonPath.read(escalation(escalationId), "$.status")).isEqualTo("ASSIGNED");
    }

    @Test
    void onlyTheAssigneeCanResolveOrHandBackAnEscalation() {
        String escalationId = escalate(null);

        assertThat(act(escalationId, "resolve", ANA)).isEqualTo(409);
        assertThat(act(escalationId, "assign", ANA)).isEqualTo(200);
        assertThat(act(escalationId, "resolve", BEN)).isEqualTo(409);
        assertThat(act(escalationId, "hand-back", BEN)).isEqualTo(409);

        assertThat(act(escalationId, "hand-back", ANA)).isEqualTo(200);
        assertThat((String) JsonPath.read(escalation(escalationId), "$.status")).isEqualTo("OPEN");
        assertThat(act(escalationId, "assign", BEN)).isEqualTo(200);
        assertThat(act(escalationId, "resolve", BEN)).isEqualTo(200);

        String resolved = escalation(escalationId);
        assertThat((String) JsonPath.read(resolved, "$.status")).isEqualTo("RESOLVED");
        assertThat((String) JsonPath.read(resolved, "$.resolvedBy")).isEqualTo(BEN);
        assertThat(act(escalationId, "assign", ANA)).isEqualTo(409);
    }

    @Test
    void onlyThePersonWorkingTheEscalationCanRetryTheOrdersRefund() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(aResponse().withStatus(503)));
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(503)));
        String refundRequestId = JsonPath.read(text(call(exceptionsAgent, "issue_refund", Map.of("orderId",
                orderId.toString(), "amount", 39.50, "reason", "item out of stock", "idempotencyKey",
                "refund-" + orderId))), "$.refundRequestId");
        String escalationId = escalate(orderId);
        stubRefundSucceeds(orderId);

        assertThat(retry(refundRequestId, BEN)).isEqualTo(409);
        assertThat(act(escalationId, "assign", ANA)).isEqualTo(200);
        assertThat(retry(refundRequestId, BEN)).isEqualTo(409);
        assertThat(refundStatus(orderId)).isEqualTo("FAILED");

        assertThat(retry(refundRequestId, ANA)).isEqualTo(200);
        assertThat(refundStatus(orderId)).isEqualTo("EXECUTED");
    }

    @Test
    void anAgentCannotRetryARefundOnceTheOrderIsWithAPerson() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(aResponse().withStatus(503)));
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(503)));
        Map<String, Object> refund = Map.of("orderId", orderId.toString(), "amount", 39.50, "reason",
                "item out of stock", "idempotencyKey", "refund-" + orderId);
        String refundRequestId = JsonPath.read(text(call(exceptionsAgent, "issue_refund", refund)), "$.refundRequestId");
        String escalationId = escalate(orderId);
        act(escalationId, "assign", ANA);
        stubRefundSucceeds(orderId);

        McpSchema.CallToolResult retriedByAgent = call(exceptionsAgent, "issue_refund", refund);

        assertThat(retriedByAgent.isError()).isTrue();
        assertThat(text(retriedByAgent)).contains("handed to a human");
        assertThat(refundStatus(orderId)).isEqualTo("FAILED");
        assertThat(retry(refundRequestId, ANA)).isEqualTo(200);
        assertThat(refundStatus(orderId)).isEqualTo("EXECUTED");
    }

    @Test
    void anOrderHandedOverAgainOrTwiceAtOnceKeepsOneEscalation() throws Exception {
        UUID orderId = UUID.randomUUID();

        List<String> ids = new CopyOnWriteArrayList<>();
        runTogether(() -> ids.add(escalate(orderId)) ? 200 : 500, () -> ids.add(escalate(orderId)) ? 200 : 500);
        String later = escalate(orderId);

        assertThat(ids).hasSize(2).containsOnly(later);
        String escalations = rest().get().uri("/api/escalations").retrieve().body(String.class);
        List<String> forOrder = JsonPath.read(escalations, "$[?(@.orderId == '" + orderId + "')].id");
        assertThat(forOrder).containsExactly(later);
        List<String> summaries = JsonPath.read(rest().get().uri("/api/orders/{orderId}/timeline", orderId).retrieve()
                .body(String.class), "$[?(@.action == 'escalate_to_human')].summary");
        assertThat(summaries).containsExactly("Handed over to a human",
                "Already with a human; added to the open escalation", "Already with a human; added to the open escalation");
    }

    @Test
    void aRefundTheProcessorFailsAfterwardsIsMarkedFailedAndHandedToAPersonOnce() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        call(exceptionsAgent, "issue_refund", Map.of("orderId", orderId.toString(), "amount", 39.50, "reason",
                "item out of stock", "idempotencyKey", "refund-" + orderId));
        assertThat(refundStatus(orderId)).isEqualTo("EXECUTED");
        String refundFailed = """
                {"eventId": "%s", "type": "REFUND_FAILED", "orderId": "%s", "idempotencyKey": "refund-%s",
                 "amount": 39.50, "currency": "EUR", "occurredAt": "2026-10-02T10:30:00Z"}"""
                .formatted(UUID.randomUUID(), orderId, orderId);

        kafka.send("payment.events", orderId.toString(), refundFailed).join();
        kafka.send("payment.events", orderId.toString(), refundFailed).join();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(refundStatus(orderId)).isEqualTo("FAILED"));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            String timeline = rest().get().uri("/api/orders/{orderId}/timeline", orderId).retrieve().body(String.class);
            assertThat((List<String>) JsonPath.read(timeline, "$[?(@.action == 'refund_failed')].actor"))
                    .containsExactly("payment-service");
            assertThat((List<String>) JsonPath.read(timeline, "$[?(@.action == 'escalate_to_human')].summary"))
                    .containsExactly("Handed over to a human");
        });
    }

    @Test
    void anAgentCanHandWorkToAPersonEvenWhenTheOrderServiceIsDown() {
        UUID orderId = UUID.randomUUID();
        SERVICES.stubFor(get("/api/orders/" + orderId).willReturn(aResponse().withStatus(503)));

        McpSchema.CallToolResult result = call(exceptionsAgent, "escalate_to_human",
                Map.of("orderId", orderId.toString(), "summary", "the order service is down"));

        assertThat(result.isError()).isFalse();
    }

    @Test
    void everyStepIsInTheAuditTrail() {
        UUID orderId = UUID.randomUUID();
        String escalationId = escalate(orderId);

        act(escalationId, "assign", ANA);
        act(escalationId, "hand-back", ANA);
        act(escalationId, "assign", BEN);
        act(escalationId, "resolve", BEN);

        List<String> actions = JsonPath.read(rest().get().uri("/api/orders/{orderId}/timeline", orderId).retrieve()
                .body(String.class), "$[*].action");
        assertThat(actions).containsExactly("escalate_to_human", "assign_escalation", "hand_back_escalation",
                "assign_escalation", "resolve_escalation");
    }

    private String escalate(UUID orderId) {
        Map<String, Object> arguments = orderId == null
                ? Map.of("summary", "payments keep failing")
                : Map.of("orderId", orderId.toString(), "summary", "refund failed twice, retry it");
        return JsonPath.read(text(call(exceptionsAgent, "escalate_to_human", arguments)), "$.id");
    }

    private String refundStatus(UUID orderId) {
        return JsonPath.read(rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class),
                "$[0].status");
    }

    private String escalation(String escalationId) {
        String escalations = rest().get().uri("/api/escalations").retrieve().body(String.class);
        List<Map<String, Object>> matches = JsonPath.read(escalations, "$[?(@.id == '" + escalationId + "')]");
        return JsonPath.parse(matches.getFirst()).jsonString();
    }

    private int act(String escalationId, String action, String person) {
        return rest().post().uri("/api/escalations/{id}/" + action, escalationId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("by", person, "note", "checked the order"))
                .exchange((request, response) -> response.getStatusCode().value());
    }

    private int retry(String refundRequestId, String person) {
        return rest().post().uri("/api/refund-requests/{id}/retry", refundRequestId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("by", person))
                .exchange((request, response) -> response.getStatusCode().value());
    }
}
