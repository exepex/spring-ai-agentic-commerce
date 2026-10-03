package io.github.exepex.commerce.servicenow.governance;

import io.github.exepex.commerce.servicenow.constants.AuditValues;
import io.github.exepex.commerce.servicenow.constants.AuthValues;
import io.github.exepex.commerce.servicenow.constants.ToolNames;
import io.github.exepex.commerce.servicenow.exception.AgentSwitchedOffException;
import io.github.exepex.commerce.servicenow.exception.ToolNotPermittedException;
import io.github.exepex.commerce.servicenow.exception.ToolRefusedException;
import io.github.exepex.commerce.servicenow.governance.dto.ToolCall;
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

    private final AgentRegistry agents;
    private final GovernanceApi governance;

    /**
     * @param summary what the call does, for the audit trail, naming the incident
     * @throws ToolRefusedException when the agent may not make the call, or the tool refuses it
     */
    public <T> T run(McpTransportContext context, String tool, String summary, Function<String, T> action) {
        var agentId = CallingAgent.of(context);
        if (!agents.mayCall(agentId, tool)) {
            record(agentId, tool, AuditValues.DENIED, AuditValues.NOT_PERMITTED.formatted(summary));
            throw new ToolNotPermittedException(agentId, tool);
        }
        if (!ToolNames.ASSIGN_TO_TEAM.equals(tool) && !isSwitchedOn(agentId)) {
            record(agentId, tool, AuditValues.DENIED, AuditValues.SWITCHED_OFF.formatted(summary));
            throw new AgentSwitchedOffException(agentId);
        }
        try {
            var result = action.apply(agentId);
            record(agentId, tool, AuditValues.SUCCEEDED, summary);
            return result;
        } catch (ToolRefusedException refused) {
            record(agentId, tool, AuditValues.DENIED, AuditValues.NOT_DONE.formatted(summary, refused.getMessage()));
            throw refused;
        } catch (RuntimeException failure) {
            record(agentId, tool, AuditValues.FAILED, AuditValues.NOT_DONE.formatted(summary, failure.getMessage()));
            throw failure;
        }
    }

    /** Fails closed: a switch that cannot be read counts as off. */
    private boolean isSwitchedOn(String agentId) {
        try {
            return Boolean.TRUE.equals(governance.switches().get(agentId));
        } catch (RuntimeException unreadable) {
            log.warn("Could not read the kill switch of {}; refusing its call", LogValues.safe(agentId), unreadable);
            return false;
        }
    }

    /**
     * Records the call as the agent. If the audit trail cannot be reached the call still counts: ServiceNow itself
     * keeps the incident's history, and the failure is logged.
     */
    private void record(String agentId, String tool, String outcome, String summary) {
        try {
            governance.recordToolCall(AuthValues.BEARER_PREFIX + agents.tokenOf(agentId),
                    new ToolCall(null, AuditValues.ACTION_PREFIX + tool, outcome, summary, null));
        } catch (RuntimeException unreachable) {
            log.warn("Could not record {} by {} in the audit trail: {}", LogValues.safe(tool), LogValues.safe(agentId),
                    LogValues.safe(summary), unreachable);
        }
    }
}
