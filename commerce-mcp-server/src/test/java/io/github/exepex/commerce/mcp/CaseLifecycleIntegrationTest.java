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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

/**
 * Every problem that needs handling becomes a case: one per order and type, carried to ServiceNow by its poller, which
 * reports back who has the incident. While a team has it, agents leave the order's money alone.
 */
class CaseLifecycleIntegrationTest extends McpServerTestSupport {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Test
    void theSameProblemRaisedAgainOrTwiceAtOnceKeepsOneCaseAndAddsNotes() throws Exception {
        UUID orderId = stubOrder("ada@example.com", "39.50");

        runTogether(() -> handOff(orderId, "first"), () -> handOff(orderId, "second"));
        handOff(orderId, "third");

        List<String> openCases = JsonPath.read(cases(orderId), "$[*].status");
        assertThat(openCases).containsExactly("PENDING");
        String outgoing = outgoingFor(orderId);
        assertThat((List<String>) JsonPath.read(outgoing, "$[*].unsentNotes[*].text")).hasSize(2);
        assertThat((String) JsonPath.read(outgoing, "$[0].supportCase.title")).startsWith("[HANDOFF] Order ");
        List<String> summaries = JsonPath.read(timeline(orderId), "$[?(@.action == 'raise_case')].summary");
        assertThat(summaries).hasSize(3);
        assertThat(summaries.getFirst()).startsWith("Opened a HANDOFF case");
        assertThat(summaries.subList(1, 3)).allMatch(summary -> summary.startsWith("Added to the open HANDOFF case"));
    }

    @Test
    void aStockOutDeliveredAgainOpensOneCasePerOrderItNames() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String stockOut = """
                {"eventId": "%s", "occurredAt": "2026-10-02T09:15:00Z", "productId": "%s", "sku": "RUN-SHOE-BLUE-43",
                 "onHand": 0, "reserved": 2, "shortfall": 2, "reason": "water damage", "affectedOrderIds": ["%s", "%s"]}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), first, second);

        kafka.send("inventory.stock-out", "product", stockOut).join();
        kafka.send("inventory.stock-out", "product", stockOut).join();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat((List<String>) JsonPath.read(cases(first), "$[*].type")).containsExactly("STOCK_OUT");
            assertThat((List<String>) JsonPath.read(cases(second), "$[*].type")).containsExactly("STOCK_OUT");
        });
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat((List<?>) JsonPath.read(outgoingFor(first), "$[0].unsentNotes")).isEmpty();
            assertThat((String) JsonPath.read(cases(first), "$[0].description")).contains("water damage");
        });
    }

    @Test
    void aFailedDeliveryAndALostParcelEachOpenACase() {
        UUID failed = UUID.randomUUID();
        UUID lost = UUID.randomUUID();

        kafka.send("shipment.events", failed.toString(), shipmentEvent(failed, "SHIPMENT_DELIVERY_FAILED",
                "Nobody home, parcel returned")).join();
        kafka.send("shipment.events", lost.toString(), shipmentEvent(lost, "SHIPMENT_LOST", "The carrier lost the parcel"))
                .join();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat((List<String>) JsonPath.read(cases(failed), "$[*].type")).containsExactly("DELIVERY_FAILED");
            assertThat((List<String>) JsonPath.read(cases(lost), "$[*].type")).containsExactly("PARCEL_LOST");
        });
        assertThat((String) JsonPath.read(cases(failed), "$[0].description")).contains("Nobody home, parcel returned");
    }

    @Test
    void aRefundTheProcessorFailsAfterwardsIsMarkedFailedAndOpensOneCase() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        call(incidentAgent, "issue_refund", Map.of("orderId", orderId.toString(), "amount", 39.50, "reason",
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
            assertThat((List<String>) JsonPath.read(timeline(orderId), "$[?(@.action == 'refund_failed')].actor"))
                    .containsExactly("payment-service");
            assertThat((List<String>) JsonPath.read(cases(orderId), "$[*].type")).containsExactly("REFUND_FAILED");
        });
    }

    @Test
    void thePollerCarriesTheCaseToServiceNowAndFollowsItsIncident() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        handOff(orderId, "first");
        handOff(orderId, "second");
        String caseId = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        String noteId = JsonPath.read(outgoingFor(orderId), "$[0].unsentNotes[0].id");

        assertThat(sync("/api/agent/cases/{id}/incident", caseId, Map.of("number", "INC0010001",
                "url", "https://example.service-now.com/incident.do?sysparm_query=number=INC0010001"))).isEqualTo(200);
        assertThat(sync("/api/agent/cases/{id}/incident", caseId, Map.of("number", "INC0010001"))).isEqualTo(200);
        assertThat(sync("/api/agent/cases/{id}/notes/" + noteId + "/sent", caseId, Map.of())).isEqualTo(200);

        assertThat(outgoingFor(orderId)).isEqualTo("[]");
        assertThat((String) JsonPath.read(cases(orderId), "$[0].status")).isEqualTo("WITH_AGENT");
        assertThat((String) JsonPath.read(cases(orderId), "$[0].incidentNumber")).isEqualTo("INC0010001");

        assertThat(followIncident(caseId, "INC0010001", "WITH_TEAM", "Payments")).isEqualTo(200);
        assertThat(followIncident(caseId, "INC0010001", "WITH_TEAM", "Payments")).isEqualTo(200);
        assertThat(followIncident(caseId, "INC0099999", "RESOLVED", "Payments")).isEqualTo(409);
        assertThat(followIncident(caseId, "INC0010001", "RESOLVED", "Payments")).isEqualTo(200);

        List<String> steps = JsonPath.read(timeline(orderId),
                "$[?(@.action == 'open_incident' || @.action == 'follow_incident')].summary");
        assertThat(steps).containsExactly("Opened ServiceNow incident INC0010001 for the HANDOFF case",
                "INC0010001 is assigned to Payments", "INC0010001 is resolved");
        handOff(orderId, "again");
        List<String> statuses = JsonPath.read(cases(orderId), "$[*].status");
        assertThat(statuses).containsExactly("RESOLVED", "PENDING");
    }

    @Test
    void whatWasRaisedAgainButNeverReachedAResolvedIncidentGoesToANewCase() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        handOff(orderId, "first");
        String caseId = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", caseId, Map.of("number", "INC0010003"));
        handOff(orderId, "the customer called again");

        assertThat(followIncident(caseId, "INC0010003", "RESOLVED", "Online Shop Agent")).isEqualTo(200);

        List<String> statuses = JsonPath.read(cases(orderId), "$[*].status");
        assertThat(statuses).containsExactly("RESOLVED", "PENDING");
        String outgoing = outgoingFor(orderId);
        assertThat((List<String>) JsonPath.read(outgoing, "$[*].supportCase.id")).doesNotContain(caseId);
        assertThat((String) JsonPath.read(outgoing, "$[0].supportCase.description"))
                .startsWith("Raised again after INC0010003 was resolved");
        assertThat((List<String>) JsonPath.read(outgoing, "$[0].unsentNotes[*].text"))
                .containsExactly("the customer called again");
    }

    @Test
    void whileATeamHasTheOrdersIncidentAgentsLeaveItsMoneyAloneButAPersonCanRetryTheRefund() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(aResponse().withStatus(503)));
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(503)));
        Map<String, Object> refund = Map.of("orderId", orderId.toString(), "amount", 39.50, "reason",
                "item out of stock", "idempotencyKey", "refund-" + orderId);
        String refundRequestId = JsonPath.read(text(call(incidentAgent, "issue_refund", refund)), "$.refundRequestId");
        handOff(orderId, "the refund keeps failing");
        String caseId = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", caseId, Map.of("number", "INC0010002"));
        assertThat(followIncident(caseId, "INC0010002", "WITH_TEAM", "Payments")).isEqualTo(200);
        stubRefundSucceeds(orderId);

        McpSchema.CallToolResult retriedByAgent = call(incidentAgent, "issue_refund", refund);
        McpSchema.CallToolResult newRefundByAgent = call(incidentAgent, "issue_refund", Map.of("orderId",
                orderId.toString(), "amount", 39.50, "reason", "item out of stock", "idempotencyKey",
                "refund-" + orderId + "-again"));

        assertThat(retriedByAgent.isError()).isTrue();
        assertThat(text(retriedByAgent)).contains("with the Payments team in ServiceNow (INC0010002)");
        assertThat(newRefundByAgent.isError()).isTrue();
        assertThat(rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class))
                .doesNotContain("-again");
        assertThat(refundStatus(orderId)).isEqualTo("FAILED");
        assertThat(retry(refundRequestId, "ana@trailhead.example")).isEqualTo(200);
        assertThat(refundStatus(orderId)).isEqualTo("EXECUTED");
    }

    @Test
    void whileAnOrderHasAnOpenCaseOnlyTheAgentThatWorksCasesMayRefundIt() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        handOff(orderId, "the customer wants to talk to a person");
        String caseId = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", caseId, Map.of("number", "INC0010004"));

        McpSchema.CallToolResult byTheAssistant = call(assistant, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 39.50, "reason", "changed my mind", "idempotencyKey", "refund-" + orderId + "-cancel",
                "customerEmail", "ada@example.com"));
        McpSchema.CallToolResult byTheIncidentAgent = call(incidentAgent, "issue_refund", Map.of("orderId",
                orderId.toString(), "amount", 39.50, "reason", "changed my mind", "idempotencyKey",
                "refund-" + orderId + "-INC0010004"));

        assertThat(byTheAssistant.isError()).isTrue();
        assertThat(text(byTheAssistant)).contains("open HANDOFF case (INC0010004)");
        assertThat(byTheIncidentAgent.isError()).isFalse();
        assertThat(refundStatus(orderId)).isEqualTo("EXECUTED");
    }

    @Test
    void onlyTheCaseWorkerMaySyncCases() {
        int asAssistant = RestClient.create("http://localhost:" + port).get().uri("/api/agent/cases/outgoing")
                .header("Authorization", "Bearer " + ASSISTANT_TOKEN)
                .exchange((request, response) -> response.getStatusCode().value());
        int anonymous = rest().get().uri("/api/agent/cases/outgoing")
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(asAssistant).isEqualTo(403);
        assertThat(anonymous).isEqualTo(401);
    }

    private int handOff(UUID orderId, String summary) {
        McpSchema.CallToolResult result = call(assistant, "escalate_to_human", Map.of("orderId", orderId.toString(),
                "summary", summary, "customerEmail", "ada@example.com"));
        return Boolean.TRUE.equals(result.isError()) ? 500 : 200;
    }

    private String cases(UUID orderId) {
        return rest().get().uri("/api/cases?orderId={id}", orderId).retrieve().body(String.class);
    }

    /** The outgoing cases of one order: other tests leave cases of their own. */
    private String outgoingFor(UUID orderId) {
        String outgoing = asCaseWorker().get().uri("/api/agent/cases/outgoing").retrieve().body(String.class);
        List<Object> forOrder = JsonPath.read(outgoing, "$[?(@.supportCase.orderId == '" + orderId + "')]");
        return JsonPath.parse(forOrder).jsonString();
    }

    private int followIncident(String caseId, String number, String status, String group) {
        return sync("/api/agent/cases/{id}/incident-state", caseId, Map.of("number", number, "status", status,
                "assignmentGroup", group));
    }

    private int sync(String path, String caseId, Map<String, Object> body) {
        return asCaseWorker().post().uri(path, caseId).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> response.getStatusCode().value());
    }

    private RestClient asCaseWorker() {
        return RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + INCIDENT_AGENT_TOKEN)
                .build();
    }

    private String timeline(UUID orderId) {
        return rest().get().uri("/api/orders/{orderId}/timeline", orderId).retrieve().body(String.class);
    }

    private String refundStatus(UUID orderId) {
        return JsonPath.read(rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class),
                "$[0].status");
    }

    private int retry(String refundRequestId, String person) {
        return rest().post().uri("/api/refund-requests/{id}/retry", refundRequestId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("by", person))
                .exchange((request, response) -> response.getStatusCode().value());
    }

    private static String shipmentEvent(UUID orderId, String type, String deliveryProblem) {
        return """
                {"eventId": "%s", "type": "%s", "orderId": "%s", "customerEmail": "ada@example.com",
                 "trackingNumber": "ACTEST000009", "deliveryProblem": "%s", "occurredAt": "2026-10-02T11:00:00Z"}"""
                .formatted(UUID.randomUUID(), type, orderId, deliveryProblem);
    }
}
