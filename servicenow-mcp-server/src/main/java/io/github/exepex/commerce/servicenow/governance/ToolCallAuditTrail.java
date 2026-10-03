package io.github.exepex.commerce.servicenow.governance;

import io.github.exepex.commerce.governance.api.client.AgentGovernanceClient;
import io.github.exepex.commerce.governance.api.dto.ToolCallOutcome;
import io.github.exepex.commerce.governance.api.dto.ToolCallReport;
import io.github.exepex.commerce.mcpserver.guard.StoppedCallOutcome;
import io.github.exepex.commerce.mcpserver.guard.ToolCall;
import io.github.exepex.commerce.mcpserver.guard.ToolCallAudit;
import io.github.exepex.commerce.mcpserver.security.AgentRegistry;
import io.github.exepex.commerce.platform.logging.LogValues;
import io.github.exepex.commerce.platform.security.BearerTokens;
import io.github.exepex.commerce.servicenow.constants.AuditValues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Records each ServiceNow tool call in the commerce MCP server's audit trail, under the agent's own token. If the
 * audit trail cannot be reached the call still counts: ServiceNow itself keeps the incident's history, and the failure
 * is logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ToolCallAuditTrail implements ToolCallAudit {

    private final AgentRegistry agents;
    private final AgentGovernanceClient governance;

    @Override
    public void notPermitted(String agentId, ToolCall call) {
        report(agentId, call, ToolCallOutcome.DENIED, AuditValues.NOT_PERMITTED.formatted(call.summary()));
    }

    @Override
    public void switchedOff(String agentId, ToolCall call) {
        report(agentId, call, ToolCallOutcome.DENIED, AuditValues.SWITCHED_OFF.formatted(call.summary()));
    }

    @Override
    public void succeeded(String agentId, ToolCall call) {
        report(agentId, call, ToolCallOutcome.SUCCEEDED, call.summary());
    }

    @Override
    public void stopped(String agentId, ToolCall call, StoppedCallOutcome outcome, String reason) {
        report(agentId, call, ToolCallOutcome.valueOf(outcome.name()),
                AuditValues.NOT_DONE.formatted(call.summary(), reason));
    }

    private void report(String agentId, ToolCall call, ToolCallOutcome outcome, String summary) {
        try {
            governance.recordToolCall(BearerTokens.authorization(agents.tokenOf(agentId)),
                    new ToolCallReport(call.orderId(), AuditValues.ACTION_PREFIX + call.tool(), outcome, summary,
                            null));
        } catch (RuntimeException unreachable) {
            log.warn("Could not record {} by {} in the audit trail: {}", LogValues.safe(call.tool()),
                    LogValues.safe(agentId), LogValues.safe(summary), unreachable);
        }
    }
}
