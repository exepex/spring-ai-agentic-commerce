package io.github.exepex.commerce.mcpserver.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.mcpserver.exception.UnauthenticatedToolCallException;
import io.github.exepex.commerce.mcpserver.security.AgentRegistry;
import io.github.exepex.commerce.mcpserver.security.AgentToken;
import io.github.exepex.commerce.mcpserver.security.McpServerProperties;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GovernedToolCallsTest {

    static class Refused extends RuntimeException {

        Refused(String message) {
            super(message);
        }
    }

    private final List<String> recorded = new ArrayList<>();
    private final HashSet<String> switchedOff = new HashSet<String>();

    private final KillSwitch killSwitch = new KillSwitch() {

        @Override
        public boolean isSwitchedOn(String agentId) {
            return !switchedOff.contains(agentId);
        }

        @Override
        public boolean worksWhileSwitchedOff(String tool) {
            return "escalate_to_human".equals(tool);
        }
    };

    private final ToolRefusals refusals = new ToolRefusals() {

        @Override
        public RuntimeException notPermitted(String agentId, String tool) {
            return new Refused(agentId + " may not call " + tool);
        }

        @Override
        public RuntimeException switchedOff(String agentId) {
            return new Refused(agentId + " is switched off");
        }

        @Override
        public StoppedCallOutcome outcomeOf(RuntimeException stopped) {
            return stopped instanceof Refused ? StoppedCallOutcome.DENIED : StoppedCallOutcome.FAILED;
        }
    };

    private final ToolCallAudit audit = new ToolCallAudit() {

        @Override
        public void notPermitted(String agentId, ToolCall call) {
            recorded.add("not permitted: " + call.tool());
        }

        @Override
        public void switchedOff(String agentId, ToolCall call) {
            recorded.add("switched off: " + call.tool());
        }

        @Override
        public void succeeded(String agentId, ToolCall call) {
            recorded.add("succeeded: " + call.summary());
        }

        @Override
        public void stopped(String agentId, ToolCall call, StoppedCallOutcome outcome, String reason) {
            recorded.add(outcome + ": " + reason);
        }
    };

    private final GovernedToolCalls calls = new GovernedToolCalls(new AgentRegistry(
            new McpServerProperties(Map.of("shopping-assistant", new AgentToken("assistant-token"),
                    "incident-agent", new AgentToken("incident-token")), List.of("/mcp")),
            AgentDefinitions.load(), AgentDefinition::commerceTools), killSwitch, refusals, audit);

    private static McpTransportContext as(String agentId) {
        return McpTransportContext.create(Map.of("agentId", agentId));
    }

    @Test
    void anAllowedCallRunsAsTheCallingAgentAndIsRecorded() {
        var result = calls.run(as("shopping-assistant"), ToolCall.of("search_products", "Searched for headlamps"),
                agentId -> "found by " + agentId);

        assertThat(result).isEqualTo("found by shopping-assistant");
        assertThat(recorded).containsExactly("succeeded: Searched for headlamps");
    }

    @Test
    void aToolOutsideTheAgentsAllowlistIsDeniedBeforeAnythingRuns() {
        var assistant = as("shopping-assistant");
        var notify = ToolCall.of("notify_customer", "Told them");

        assertThatThrownBy(() -> calls.run(assistant, notify, agentId -> fail()))
                .isInstanceOf(Refused.class)
                .hasMessage("shopping-assistant may not call notify_customer");
        assertThat(recorded).containsExactly("not permitted: notify_customer");
    }

    @Test
    void aSwitchedOffAgentIsStoppedButMayStillHandWorkToPeople() {
        switchedOff.add("shopping-assistant");
        var assistant = as("shopping-assistant");
        var search = ToolCall.of("search_products", "Searched");

        assertThatThrownBy(() -> calls.run(assistant, search, agentId -> fail()))
                .hasMessage("shopping-assistant is switched off");
        calls.run(assistant, ToolCall.of("escalate_to_human", "Handed over"), agentId -> "handed over");

        assertThat(recorded).containsExactly("switched off: search_products", "succeeded: Handed over");
    }

    @Test
    void aRuleThatStopsTheToolIsDeniedAndAnythingElseFailed() {
        var incidentAgent = as("incident-agent");
        var refund = ToolCall.of("issue_refund", "Refunded");

        assertThatThrownBy(() -> calls.run(incidentAgent, refund, agentId -> { throw new Refused("above the limit"); }))
                .isInstanceOf(Refused.class);
        assertThatThrownBy(() -> calls.run(incidentAgent, refund,
                agentId -> { throw new IllegalStateException("payments down"); }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(recorded).containsExactly("DENIED: above the limit", "FAILED: payments down");
    }

    @Test
    void aToolThatRecordsItsOwnSuccessIsNotRecordedTwice() {
        calls.run(as("incident-agent"), new ToolCall("issue_refund", "Refunded", null, true), agentId -> "refunded");

        assertThat(recorded).isEmpty();
    }

    @Test
    void aCallWithoutAnAuthenticatedAgentNeverRuns() {
        var anonymous = McpTransportContext.create(Map.of());
        var search = ToolCall.of("search_products", "x");

        assertThatThrownBy(() -> calls.run(anonymous, search, agentId -> fail()))
                .isInstanceOf(UnauthenticatedToolCallException.class);
        assertThat(recorded).isEmpty();
    }

    private static String fail() {
        throw new AssertionError("the tool must not run");
    }
}
