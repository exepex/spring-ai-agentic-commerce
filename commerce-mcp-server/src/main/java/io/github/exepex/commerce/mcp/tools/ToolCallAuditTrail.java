package io.github.exepex.commerce.mcp.tools;

import io.github.exepex.commerce.mcp.constants.ToolMessages;
import io.github.exepex.commerce.mcp.governance.AuditEvent;
import io.github.exepex.commerce.mcp.governance.AuditTrail;
import io.github.exepex.commerce.mcpserver.guard.ToolCall;
import io.github.exepex.commerce.mcpserver.guard.StoppedCallOutcome;
import io.github.exepex.commerce.mcpserver.guard.ToolCallAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Records each tool call in the audit trail under the tool's name and the calling agent's: a refused call with why,
 * and the call's own summary as details; a stopped one with what it was asked to do and why it did not.
 */
@Component
@RequiredArgsConstructor
class ToolCallAuditTrail implements ToolCallAudit {

    private final AuditTrail audit;

    @Override
    public void notPermitted(String agentId, ToolCall call) {
        audit.record(call.orderId(), AuditEvent.ActorType.AGENT, agentId, call.tool(), AuditEvent.Outcome.DENIED,
                ToolMessages.NOT_PERMITTED, call.summary());
    }

    @Override
    public void switchedOff(String agentId, ToolCall call) {
        audit.record(call.orderId(), AuditEvent.ActorType.AGENT, agentId, call.tool(), AuditEvent.Outcome.DENIED,
                ToolMessages.SWITCHED_OFF, call.summary());
    }

    @Override
    public void succeeded(String agentId, ToolCall call) {
        audit.record(call.orderId(), AuditEvent.ActorType.AGENT, agentId, call.tool(), AuditEvent.Outcome.SUCCEEDED,
                call.summary(), null);
    }

    @Override
    public void stopped(String agentId, ToolCall call, StoppedCallOutcome outcome, String reason) {
        audit.record(call.orderId(), AuditEvent.ActorType.AGENT, agentId, call.tool(),
                AuditEvent.Outcome.valueOf(outcome.name()), ToolMessages.STOPPED_CALL.formatted(call.summary(), reason),
                null);
    }
}
