package io.github.exepex.commerce.mcp;

import com.jayway.jsonpath.JsonPath;
import io.github.exepex.commerce.platform.security.BearerTokens;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

/**
 * What the case tests share: raising a problem the way an agent hands it off, reading an order's cases, and acting
 * as the ServiceNow MCP server's poller, which syncs cases with the incident agent's token.
 */
abstract class CaseTestSupport extends McpServerTestSupport {

    @Autowired
    protected KafkaTemplate<String, String> kafka;

    protected int recordServiceDeskIncident(Map<String, Object> incident) {
        return asCaseWorker().post().uri("/api/agent/cases/service-desk").contentType(MediaType.APPLICATION_JSON)
                .body(incident).exchange((request, response) -> response.getStatusCode().value());
    }

    protected int handOff(UUID orderId, String summary) {
        McpSchema.CallToolResult result = call(assistant, "escalate_to_human", Map.of("orderId", orderId.toString(),
                "summary", summary, "customerEmail", "ada@example.com"));
        return Boolean.TRUE.equals(result.isError()) ? 500 : 200;
    }

    protected String cases(UUID orderId) {
        return rest().get().uri("/api/cases?orderId={id}", orderId).retrieve().body(String.class);
    }

    /** The outgoing cases of one order: other tests leave cases of their own. */
    protected String outgoingFor(UUID orderId) {
        String outgoing = asCaseWorker().get().uri("/api/agent/cases/outgoing").retrieve().body(String.class);
        List<Object> forOrder = JsonPath.read(outgoing, "$[?(@.supportCase.orderId == '" + orderId + "')]");
        return JsonPath.parse(forOrder).jsonString();
    }

    /** The ids of the cases the poller reads back. */
    protected List<String> inServiceNow() {
        return JsonPath.read(asCaseWorker().get().uri("/api/agent/cases/in-servicenow").retrieve().body(String.class),
                "$[*].id");
    }

    protected int followIncident(String caseId, String number, String status, String group, boolean incidentFinal) {
        return sync("/api/agent/cases/{id}/incident-state", caseId, Map.of("number", number, "status", status,
                "assignmentGroup", group, "incidentFinal", incidentFinal));
    }

    protected int followIncident(String caseId, String number, String status, String group) {
        return followIncident(caseId, number, status, group, false);
    }

    protected int sync(String path, String caseId, Map<String, Object> body) {
        return asCaseWorker().post().uri(path, caseId).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> response.getStatusCode().value());
    }

    protected RestClient asCaseWorker() {
        return RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", BearerTokens.authorization(INCIDENT_AGENT_TOKEN))
                .build();
    }

    protected String refundStatus(UUID orderId) {
        return JsonPath.read(rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class),
                "$[0].status");
    }

    protected int retry(String refundRequestId, String person) {
        return rest().post().uri("/api/refund-requests/{id}/retry", refundRequestId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("by", person))
                .exchange((request, response) -> response.getStatusCode().value());
    }

    protected static String shipmentEvent(UUID orderId, String type, String deliveryProblem) {
        return """
                {"eventId": "%s", "type": "%s", "orderId": "%s", "customerEmail": "ada@example.com",
                 "trackingNumber": "ACTEST000009", "deliveryProblem": "%s", "occurredAt": "2026-10-02T11:00:00Z"}"""
                .formatted(UUID.randomUUID(), type, orderId, deliveryProblem);
    }
}
