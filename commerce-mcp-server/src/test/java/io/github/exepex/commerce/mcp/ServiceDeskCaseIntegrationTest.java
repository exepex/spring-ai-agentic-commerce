package io.github.exepex.commerce.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * An incident the service desk raised about an order is recorded as a case, so agents leave the order's money to
 * whoever works it, and follows the incident when the service desk corrects its order, reopens or closes it.
 */
class ServiceDeskCaseIntegrationTest extends CaseTestSupport {

    @Test
    void anIncidentTheServiceDeskRaisedAboutAnOrderIsACaseThatKeepsOtherAgentsFromRefundingIt() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        Map<String, Object> incident = Map.of("orderId", orderId.toString(), "number", "INC0010008",
                "url", "https://dev.example.com/incident.do?sys_id=sys-8",
                "shortDescription", "Order arrived broken, the customer wants their money back",
                "status", "WITH_TEAM", "assignmentGroup", "Customer Care");

        int recorded = recordServiceDeskIncident(incident);
        int recordedAgain = recordServiceDeskIncident(incident);
        McpSchema.CallToolResult byTheAssistant = call(assistant, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 39.50, "reason", "arrived broken", "idempotencyKey", "refund-" + orderId + "-broken",
                "customerEmail", "ada@example.com"));

        assertThat(recorded).isEqualTo(200);
        assertThat(recordedAgain).isEqualTo(200);
        assertThat((List<String>) JsonPath.read(cases(orderId), "$[*].type")).containsExactly("SERVICE_DESK");
        assertThat((String) JsonPath.read(cases(orderId), "$[0].title"))
                .isEqualTo("Order arrived broken, the customer wants their money back");
        assertThat((String) JsonPath.read(cases(orderId), "$[0].incidentNumber")).isEqualTo("INC0010008");
        assertThat(byTheAssistant.isError()).isTrue();
        assertThat(text(byTheAssistant)).contains("with the Customer Care team in ServiceNow (INC0010008)");
        assertThat(JsonPath.<List<String>>read(asCaseWorker().get().uri("/api/agent/cases/in-servicenow").retrieve()
                .body(String.class), "$[*].incidentNumber")).contains("INC0010008");
    }

    @Test
    void theIncidentAgentWorkingAServiceDeskIncidentMayRefundForIt() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        assertThat(recordServiceDeskIncident(Map.of("orderId", orderId.toString(), "number", "INC0010009",
                "url", "https://dev.example.com/incident.do?sys_id=sys-9", "shortDescription", "Arrived broken",
                "status", "WITH_AGENT", "assignmentGroup", "Online Shop Agent"))).isEqualTo(200);

        McpSchema.CallToolResult forAnotherIncident = call(incidentAgent, "issue_refund", Map.of("orderId",
                orderId.toString(), "amount", 39.50, "reason", "arrived broken", "idempotencyKey",
                "refund-" + orderId + "-INC0010010", "incidentNumber", "INC0010010"));
        McpSchema.CallToolResult forItsIncident = call(incidentAgent, "issue_refund", Map.of("orderId",
                orderId.toString(), "amount", 39.50, "reason", "arrived broken", "idempotencyKey",
                "refund-" + orderId + "-INC0010009", "incidentNumber", "INC0010009"));

        assertThat(forAnotherIncident.isError()).isTrue();
        assertThat(forItsIncident.isError()).isFalse();
        assertThat(refundStatus(orderId)).isEqualTo("EXECUTED");
    }

    @Test
    void aServiceDeskIncidentWhoseOrderTheServiceDeskCorrectedTakesItsCaseToThatOrder() {
        UUID mistyped = stubOrder("ada@example.com", "39.50");
        UUID meant = stubOrder("ada@example.com", "39.50");
        Map<String, Object> incident = Map.of("orderId", mistyped.toString(), "number", "INC0010013",
                "url", "https://dev.example.com/incident.do?sys_id=sys-13", "shortDescription", "Arrived broken",
                "status", "WITH_TEAM", "assignmentGroup", "Customer Care");
        Map<String, Object> corrected = new HashMap<>(incident);
        corrected.put("orderId", meant.toString());

        assertThat(recordServiceDeskIncident(incident)).isEqualTo(200);
        assertThat(recordServiceDeskIncident(corrected)).isEqualTo(200);

        assertThat((List<?>) JsonPath.read(cases(mistyped), "$")).isEmpty();
        assertThat((List<String>) JsonPath.read(cases(meant), "$[*].incidentNumber")).containsExactly("INC0010013");
        McpSchema.CallToolResult byTheAssistant = call(assistant, "issue_refund", Map.of("orderId", meant.toString(),
                "amount", 39.50, "reason", "arrived broken", "idempotencyKey", "refund-" + meant + "-broken",
                "customerEmail", "ada@example.com"));
        assertThat(byTheAssistant.isError()).isTrue();
    }

    @Test
    void aServiceDeskIncidentReopenedAfterItsCaseWasResolvedOpensTheCaseAgain() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        Map<String, Object> incident = Map.of("orderId", orderId.toString(), "number", "INC0010014",
                "url", "https://dev.example.com/incident.do?sys_id=sys-14", "shortDescription", "Arrived broken",
                "status", "WITH_AGENT", "assignmentGroup", "Online Shop Agent");
        assertThat(recordServiceDeskIncident(incident)).isEqualTo(200);
        String caseId = JsonPath.read(cases(orderId), "$[0].id");
        assertThat(sync("/api/agent/cases/{id}/incident-state", caseId, Map.of("number", "INC0010014",
                "status", "RESOLVED", "assignmentGroup", "Online Shop Agent", "incidentFinal", false,
                "orderId", orderId.toString())))
                .isEqualTo(200);
        Map<String, Object> reopened = new HashMap<>(incident);
        reopened.put("status", "WITH_TEAM");
        reopened.put("assignmentGroup", "Payments");

        assertThat(recordServiceDeskIncident(reopened)).isEqualTo(200);

        assertThat((String) JsonPath.read(cases(orderId), "$[0].status")).isEqualTo("WITH_TEAM");
        assertThat((String) JsonPath.read(cases(orderId), "$[0].assignmentGroup")).isEqualTo("Payments");
        assertThat((List<?>) JsonPath.read(cases(orderId), "$")).hasSize(1);
    }

    @Test
    void aServiceDeskIncidentThatNoLongerNamesTheOrderFreesTheOrdersMoney() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        assertThat(recordServiceDeskIncident(Map.of("orderId", orderId.toString(), "number", "INC0010036",
                "url", "https://dev.example.com/incident.do?sys_id=sys-36", "shortDescription", "Arrived broken",
                "status", "WITH_TEAM", "assignmentGroup", "Payments"))).isEqualTo(200);
        String caseId = JsonPath.read(cases(orderId), "$[0].id");

        // The service desk cleared the incident's Correlation ID: it is not about this order after all.
        assertThat(followIncident(caseId, "INC0010036", "WITH_TEAM", "Payments")).isEqualTo(200);
        McpSchema.CallToolResult byTheAssistant = call(assistant, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 39.50, "reason", "arrived broken", "idempotencyKey", "refund-" + orderId + "-broken",
                "customerEmail", "ada@example.com"));

        assertThat((String) JsonPath.read(cases(orderId), "$[0].status")).isEqualTo("RESOLVED");
        assertThat(byTheAssistant.isError()).isFalse();
        assertThat((List<String>) JsonPath.read(timeline(orderId), "$[?(@.action == 'follow_incident')].summary"))
                .anySatisfy(summary -> assertThat(summary).startsWith("INC0010036 no longer names this order"));
    }

    @Test
    void aServiceDeskIncidentReadBackUnderAnotherOrderTakesItsCaseThere() {
        UUID mistyped = stubOrder("ada@example.com", "39.50");
        UUID meant = stubOrder("ada@example.com", "39.50");
        assertThat(recordServiceDeskIncident(Map.of("orderId", mistyped.toString(), "number", "INC0010038",
                "url", "https://dev.example.com/incident.do?sys_id=sys-38", "shortDescription", "Arrived broken",
                "status", "WITH_TEAM", "assignmentGroup", "Payments"))).isEqualTo(200);
        String caseId = JsonPath.read(cases(mistyped), "$[0].id");

        assertThat(sync("/api/agent/cases/{id}/incident-state", caseId, Map.of("number", "INC0010038",
                "status", "WITH_TEAM", "assignmentGroup", "Payments", "incidentFinal", false,
                "orderId", meant.toString()))).isEqualTo(200);

        assertThat((List<?>) JsonPath.read(cases(mistyped), "$")).isEmpty();
        assertThat((List<String>) JsonPath.read(cases(meant), "$[*].id")).containsExactly(caseId);
    }

    @Test
    void aServiceDeskIncidentMovedToAnotherOrderAsItIsResolvedSaysSoOnThatOrdersTimeline() {
        UUID mistyped = stubOrder("ada@example.com", "39.50");
        UUID meant = stubOrder("ada@example.com", "39.50");
        assertThat(recordServiceDeskIncident(Map.of("orderId", mistyped.toString(), "number", "INC0010039",
                "url", "https://dev.example.com/incident.do?sys_id=sys-39", "shortDescription", "Arrived broken",
                "status", "WITH_TEAM", "assignmentGroup", "Payments"))).isEqualTo(200);
        String caseId = JsonPath.read(cases(mistyped), "$[0].id");

        assertThat(sync("/api/agent/cases/{id}/incident-state", caseId, Map.of("number", "INC0010039",
                "status", "RESOLVED", "assignmentGroup", "Payments", "incidentFinal", false,
                "orderId", meant.toString()))).isEqualTo(200);

        List<String> steps = JsonPath.read(timeline(meant), "$[?(@.action == 'follow_incident')].summary");
        assertThat(steps).containsExactly("INC0010039 is now about this order and is resolved");
        assertThat((String) JsonPath.read(cases(meant), "$[0].status")).isEqualTo("RESOLVED");
    }

    @Test
    void aServiceDeskCaseFreedFromItsOrderIsNoLongerReadBackOnceItsIncidentIsClosed() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        assertThat(recordServiceDeskIncident(Map.of("orderId", orderId.toString(), "number", "INC0010037",
                "url", "https://dev.example.com/incident.do?sys_id=sys-37", "shortDescription", "Arrived broken",
                "status", "WITH_TEAM", "assignmentGroup", "Payments"))).isEqualTo(200);
        String caseId = JsonPath.read(cases(orderId), "$[0].id");
        followIncident(caseId, "INC0010037", "WITH_TEAM", "Payments");

        assertThat(followIncident(caseId, "INC0010037", "RESOLVED", "Payments", true)).isEqualTo(200);

        assertThat(inServiceNow()).doesNotContain(caseId);
    }

    @Test
    void anOrderCanHaveSeveralServiceDeskIncidentsAtOnceEvenWithTheSameNumberOnAnotherInstance() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        Map<String, Object> first = Map.of("orderId", orderId.toString(), "number", "INC0010011",
                "url", "https://dev.example.com/incident.do?sys_id=sys-11", "shortDescription", "Wrong colour",
                "status", "WITH_AGENT", "assignmentGroup", "Online Shop Agent");
        Map<String, Object> second = Map.of("orderId", orderId.toString(), "number", "INC0010012",
                "url", "https://dev.example.com/incident.do?sys_id=sys-12", "shortDescription", "Late delivery",
                "status", "WITH_AGENT", "assignmentGroup", "Online Shop Agent");
        // The simulator numbers its incidents from INC0010001 again each time it starts.
        Map<String, Object> sameNumberElsewhere = Map.of("orderId", orderId.toString(), "number", "INC0010011",
                "url", "http://localhost:8088/incident.do?sys_id=sim-11", "shortDescription", "Parcel damaged",
                "status", "WITH_AGENT", "assignmentGroup", "Online Shop Agent");

        assertThat(recordServiceDeskIncident(first)).isEqualTo(200);
        assertThat(recordServiceDeskIncident(second)).isEqualTo(200);
        assertThat(recordServiceDeskIncident(sameNumberElsewhere)).isEqualTo(200);

        assertThat((List<String>) JsonPath.read(cases(orderId), "$[*].title"))
                .containsExactlyInAnyOrder("Wrong colour", "Late delivery", "Parcel damaged");
    }
}
