package io.github.exepex.commerce.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The agents' kill switches are kept in the database, and the MCP server enforces them on every tool call: a
 * switched-off agent can do nothing but hand work to a human.
 */
class AgentSwitchIntegrationTest extends McpServerTestSupport {

    private static final String OPERATOR = "ana@trailhead.example";

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void switchEveryAgentBackOn() {
        switchAgent("shopping-assistant", true, OPERATOR);
        switchAgent("incident-agent", true, OPERATOR);
    }

    @Test
    void aSwitchedOffAgentCanOnlyHandWorkToPeople() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        switchAgent("shopping-assistant", false, OPERATOR);

        McpSchema.CallToolResult lookup = call(assistant, "get_order",
                Map.of("orderId", orderId.toString(), "customerEmail", "ada@example.com"));
        McpSchema.CallToolResult handOff = call(assistant, "escalate_to_human", Map.of("orderId", orderId.toString(),
                "summary", "The customer wants to talk to a person.", "customerEmail", "ada@example.com"));

        assertThat(lookup.isError()).isTrue();
        assertThat(text(lookup)).contains("switched off");
        assertThat(handOff.isError()).isFalse();
        assertThat(call(incidentAgent, "get_order", Map.of("orderId", orderId.toString())).isError())
                .as("the other agent is not affected").isFalse();
        List<String> denied = JsonPath.read(timeline(orderId), "$[?(@.outcome == 'DENIED')].action");
        assertThat(denied).containsExactly("get_order");
    }

    @Test
    void aSwitchIsKeptAndWhoChangedItIsAudited() {
        Map<String, Object> switches = switchAgent("shopping-assistant", false, OPERATOR);

        assertThat(switches).containsEntry("shopping-assistant", false).containsEntry("incident-agent", true);
        assertThat(rest().get().uri("/api/agent-switches").retrieve().body(String.class))
                .contains("\"shopping-assistant\":false");
        String audit = rest().get().uri("/api/audit-events").retrieve().body(String.class);
        List<String> by = JsonPath.read(audit, "$[?(@.action == 'switch_off_agent')].actor");
        assertThat(by).contains(OPERATOR);
    }

    @Test
    void twoFirstChangesOfASwitchAtOnceBothSucceedAndTheLastOneWins() throws Exception {
        // A fresh database has no row for the switch yet: both changes try to create it.
        jdbc.sql("delete from governance.agent_switch where agent_id = 'shopping-assistant'").update();

        List<Integer> statuses = runTogether(
                () -> switchStatus("shopping-assistant", Map.of("enabled", false, "by", OPERATOR)),
                () -> switchStatus("shopping-assistant", Map.of("enabled", false, "by", "ben@trailhead.example")));
        switchAgent("shopping-assistant", true, OPERATOR);

        assertThat(statuses).containsExactly(200, 200);
        assertThat(rest().get().uri("/api/agent-switches").retrieve().body(String.class))
                .contains("\"shopping-assistant\":true");
    }

    @Test
    void refusesAnUnknownAgentOrASwitchWithoutAPersonTheAuditCanRecord() {
        assertThat(switchStatus("no-such-agent", Map.of("enabled", false, "by", OPERATOR))).isEqualTo(404);
        assertThat(switchStatus("shopping-assistant", Map.of("enabled", false))).isEqualTo(400);
        assertThat(switchStatus("shopping-assistant", Map.of("enabled", false, "by", "x".repeat(101)))).isEqualTo(400);
        assertThat(rest().get().uri("/api/agent-switches").retrieve().body(String.class))
                .contains("\"shopping-assistant\":true");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> switchAgent(String agentId, boolean enabled, String by) {
        return rest().put().uri("/api/agent-switches/{agentId}", agentId).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled, "by", by)).retrieve().body(Map.class);
    }

    private int switchStatus(String agentId, Map<String, Object> change) {
        return rest().put().uri("/api/agent-switches/{agentId}", agentId).contentType(MediaType.APPLICATION_JSON)
                .body(change).exchange((request, response) -> response.getStatusCode().value());
    }

    private String timeline(UUID orderId) {
        return rest().get().uri("/api/orders/{orderId}/timeline", orderId).retrieve().body(String.class);
    }
}
