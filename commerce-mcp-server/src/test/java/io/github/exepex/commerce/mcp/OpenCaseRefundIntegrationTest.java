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

/**
 * While an order has an open case, agents leave its money to whoever works the case: only the incident agent may pay,
 * for the incident it is working, and only while no other incident of the order could be a person's.
 */
class OpenCaseRefundIntegrationTest extends CaseTestSupport {

    @Test
    void anIncidentReopenedWhileANewerCaseOfItsProblemIsOpenOpensItsCaseBesideItAndKeepsAgentsFromTheMoney() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        handOff(orderId, "first");
        String earlier = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", earlier, Map.of("number", "INC0010033"));
        followIncident(earlier, "INC0010033", "RESOLVED", "Online Shop Agent");
        handOff(orderId, "the customer called again");
        String newer = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", newer, Map.of("number", "INC0010034"));

        assertThat(followIncident(earlier, "INC0010033", "WITH_TEAM", "Payments")).isEqualTo(200);
        handOff(orderId, "and once more");
        McpSchema.CallToolResult forTheNewerIncident = call(incidentAgent, "issue_refund", Map.of("orderId",
                orderId.toString(), "amount", 39.50, "reason", "lost", "idempotencyKey", "refund-" + orderId + "-INC0010034",
                "incidentNumber", "INC0010034"));

        assertThat((List<String>) JsonPath.read(cases(orderId), "$[?(@.status != 'RESOLVED')].id"))
                .containsExactlyInAnyOrder(earlier, newer);
        assertThat((List<String>) JsonPath.read(outgoingFor(orderId), "$[?(@.supportCase.id == '" + newer
                + "')].unsentNotes[*].text")).anySatisfy(text -> assertThat(text).contains("once more"));
        assertThat(forTheNewerIncident.isError()).isTrue();
        assertThat(text(forTheNewerIncident)).contains("Payments");
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
                "refund-" + orderId + "-INC0010004", "incidentNumber", "INC0010004"));

        assertThat(byTheAssistant.isError()).isTrue();
        assertThat(text(byTheAssistant)).contains("open HANDOFF case (INC0010004)");
        assertThat(byTheIncidentAgent.isError()).isFalse();
        assertThat(refundStatus(orderId)).isEqualTo("EXECUTED");
    }

    @Test
    void theIncidentAgentPaysOnlyWhileNoOtherIncidentOfTheOrderCouldBeAPersons() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        handOff(orderId, "the customer wants to talk to a person");
        String handOffCase = JsonPath.read(outgoingFor(orderId), "$[0].supportCase.id");
        sync("/api/agent/cases/{id}/incident", handOffCase, Map.of("number", "INC0010006"));
        kafka.send("shipment.events", orderId.toString(), shipmentEvent(orderId, "SHIPMENT_LOST", "The carrier lost it"))
                .join();
        await().atMost(Duration.ofSeconds(20)).until(() -> ((List<?>) JsonPath.read(cases(orderId), "$")).size() == 2);
        String lostCase = JsonPath.<List<String>>read(cases(orderId), "$[?(@.type == 'PARCEL_LOST')].id").getFirst();
        sync("/api/agent/cases/{id}/incident", lostCase, Map.of("number", "INC0010007"));
        Map<String, Object> refund = Map.of("orderId", orderId.toString(), "amount", 39.50, "reason", "lost parcel",
                "idempotencyKey", "refund-" + orderId + "-INC0010007", "incidentNumber", "INC0010007");

        // A person may have taken the hand-off's incident since ServiceNow was last read.
        McpSchema.CallToolResult whileTheHandOffIsOpen = call(incidentAgent, "issue_refund", refund);
        followIncident(handOffCase, "INC0010006", "RESOLVED", "Online Shop Agent");
        McpSchema.CallToolResult onceItIsResolved = call(incidentAgent, "issue_refund", refund);

        assertThat(whileTheHandOffIsOpen.isError()).isTrue();
        assertThat(text(whileTheHandOffIsOpen)).contains("open HANDOFF case (INC0010006)");
        assertThat(onceItIsResolved.isError()).isFalse();
        assertThat(refundStatus(orderId)).isEqualTo("EXECUTED");
    }
}
