package io.github.exepex.commerce.servicenow.governance;

import io.github.exepex.commerce.servicenow.security.AgentRegistry;
import io.github.exepex.commerce.servicenow.security.CallingAgent;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Wraps every ServiceNow tool call with the same rules as the commerce MCP server: the calling agent must be allowed
 * the tool and be switched on, and the call is recorded in the shared audit trail. Handing an incident to a team is
 * allowed even when the agent is switched off, so its work always reaches a person.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolGuard {

    /** The tool that hands work to people; never blocked by the kill switch. */
    public static final String HAND_TO_TEAM = "assign_to_team";

    private final AgentRegistry agents;
    private final GovernanceApi governance;

    /**
     * @param summary what the call does, for the audit trail, naming the incident
     * @throws ToolRefusedException when the agent may not make the call, or the tool refuses it
     */
    public <T> T run(McpTransportContext context, String tool, String summary, Function<String, T> action) {
        String agentId = CallingAgent.of(context);
        if (!agents.mayCall(agentId, tool)) {
            record(agentId, tool, "DENIED", summary + ": tool not permitted for this agent");
            throw new ToolRefusedException("Agent " + agentId + " is not permitted to call " + tool);
        }
        if (!HAND_TO_TEAM.equals(tool) && !isSwitchedOn(agentId)) {
            record(agentId, tool, "DENIED", summary + ": agent is switched off");
            throw new ToolRefusedException("Agent " + agentId + " is switched off. Stop, and hand the incident to a team "
                    + "with " + HAND_TO_TEAM + ".");
        }
        try {
            T result = action.apply(agentId);
            record(agentId, tool, "SUCCEEDED", summary);
            return result;
        } catch (ToolRefusedException refused) {
            record(agentId, tool, "DENIED", summary + ": " + refused.getMessage());
            throw refused;
        } catch (RuntimeException failure) {
            record(agentId, tool, "FAILED", summary + ": " + failure.getMessage());
            throw failure;
        }
    }

    /** Fails closed: a switch that cannot be read counts as off. */
    private boolean isSwitchedOn(String agentId) {
        try {
            return Boolean.TRUE.equals(governance.switches().get(agentId));
        } catch (RuntimeException unreadable) {
            log.warn("Could not read the kill switch of {}; refusing its call", agentId, unreadable);
            return false;
        }
    }

    /**
     * Records the call as the agent. If the audit trail cannot be reached the call still counts: ServiceNow itself
     * keeps the incident's history, and the failure is logged.
     */
    private void record(String agentId, String tool, String outcome, String summary) {
        try {
            governance.recordToolCall("Bearer " + agents.tokenOf(agentId),
                    new GovernanceApi.ToolCall(null, "servicenow:" + tool, outcome, summary, null));
        } catch (RuntimeException unreachable) {
            log.warn("Could not record {} by {} in the audit trail: {}", tool, agentId, summary, unreachable);
        }
    }
}
