package io.github.exepex.commerce.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The agents' kill switches are kept in the database, and the MCP server enforces them on every tool call: a
 * switched-off agent can do nothing but hand work to a human.
 */
class AgentSwitchIntegrationTest extends McpServerTestSupport {

    private static final String OPERATOR = "ana@trailhead.example";

    @AfterEach
    void switchEveryAgentBackOn() {
        switchAgent("shopping-assistant", true, OPERATOR);
        switchAgent("order-exceptions-agent", true, OPERATOR);
    }

    @Test
    void aSwitchedOffAgentCanOnlyHandWorkToAHuman() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/products").willReturn(okJson("[]")));
        switchAgent("order-exceptions-agent", false, OPERATOR);

        McpSchema.CallToolResult lookup = call(exceptionsAgent, "get_order", Map.of("orderId", orderId.toString()));
        McpSchema.CallToolResult handOff = call(exceptionsAgent, "escalate_to_human",
                Map.of("orderId", orderId.toString(), "summary", "Stock-out; the agent is switched off."));

        assertThat(lookup.isError()).isTrue();
        assertThat(text(lookup)).contains("switched off");
        assertThat(handOff.isError()).isFalse();
        assertThat(call(assistant, "search_products", Map.of("query", "headlamp")).isError())
                .as("the other agent is not affected").isFalse();
        List<String> denied = JsonPath.read(timeline(orderId), "$[?(@.outcome == 'DENIED')].action");
        assertThat(denied).containsExactly("get_order");
    }

    @Test
    void aSwitchIsKeptAndWhoChangedItIsAudited() {
        Map<String, Object> switches = switchAgent("shopping-assistant", false, OPERATOR);

        assertThat(switches).containsEntry("shopping-assistant", false).containsEntry("order-exceptions-agent", true);
        assertThat(rest().get().uri("/api/agent-switches").retrieve().body(String.class))
                .contains("\"shopping-assistant\":false");
        String audit = rest().get().uri("/api/audit-events").retrieve().body(String.class);
        List<String> by = JsonPath.read(audit, "$[?(@.action == 'switch_off_agent')].actor");
        assertThat(by).contains(OPERATOR);
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
