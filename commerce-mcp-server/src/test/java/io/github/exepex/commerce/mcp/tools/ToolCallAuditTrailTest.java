package io.github.exepex.commerce.mcp.tools;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.exepex.commerce.mcp.governance.AuditEvent;
import io.github.exepex.commerce.mcp.governance.AuditTrail;
import io.github.exepex.commerce.mcpserver.guard.ToolCall;
import io.github.exepex.commerce.mcpserver.guard.ToolCallOutcome;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What the audit trail records of each tool call, under the tool's and the calling agent's names. */
class ToolCallAuditTrailTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final ToolCall CALL = new ToolCall("get_order", "Looked up the order", ORDER_ID, false);

    private final AuditTrail audit = mock(AuditTrail.class);
    private final ToolCallAuditTrail trail = new ToolCallAuditTrail(audit);

    @Test
    void aRefusedCallIsDeniedWithWhyAndWhatItWasAskedToDo() {
        trail.notPermitted("incident-agent", CALL);
        trail.switchedOff("shopping-assistant", CALL);

        verify(audit).record(ORDER_ID, AuditEvent.ActorType.AGENT, "incident-agent", "get_order",
                AuditEvent.Outcome.DENIED, "Tool not permitted for this agent", "Looked up the order");
        verify(audit).record(ORDER_ID, AuditEvent.ActorType.AGENT, "shopping-assistant", "get_order",
                AuditEvent.Outcome.DENIED, "Agent is switched off", "Looked up the order");
    }

    @Test
    void aCallThatSucceededIsRecordedWithItsSummary() {
        trail.succeeded("incident-agent", CALL);

        verify(audit).record(ORDER_ID, AuditEvent.ActorType.AGENT, "incident-agent", "get_order",
                AuditEvent.Outcome.SUCCEEDED, "Looked up the order", null);
    }

    @Test
    void aStoppedCallIsRecordedWithWhatStoppedIt() {
        trail.stopped("shopping-assistant", CALL, ToolCallOutcome.DENIED, "Not the customer's order");
        trail.stopped("incident-agent", CALL, ToolCallOutcome.FAILED, "The order service could not be reached.");

        verify(audit).record(ORDER_ID, AuditEvent.ActorType.AGENT, "shopping-assistant", "get_order",
                AuditEvent.Outcome.DENIED, "Looked up the order: Not the customer's order", null);
        verify(audit).record(ORDER_ID, AuditEvent.ActorType.AGENT, "incident-agent", "get_order",
                AuditEvent.Outcome.FAILED, "Looked up the order: The order service could not be reached.", null);
    }
}
