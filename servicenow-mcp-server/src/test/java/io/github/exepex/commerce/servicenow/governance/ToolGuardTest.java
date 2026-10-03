package io.github.exepex.commerce.servicenow.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.exepex.commerce.governance.api.client.AgentGovernanceClient;
import io.github.exepex.commerce.governance.api.client.AgentSwitchesClient;
import io.github.exepex.commerce.governance.api.dto.ToolCallOutcome;
import io.github.exepex.commerce.governance.api.dto.ToolCallReport;
import io.github.exepex.commerce.mcpserver.guard.GovernedToolCalls;
import io.github.exepex.commerce.mcpserver.guard.StoppedCallOutcome;
import io.github.exepex.commerce.mcpserver.guard.ToolCall;
import io.github.exepex.commerce.mcpserver.security.AgentRegistry;
import io.github.exepex.commerce.servicenow.exception.IncidentNotFoundException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The ServiceNow server's kill switch, refusals and audit trail, as the shared governed tool call uses them. */
class ToolGuardTest {

    private static final ToolCall READ = ToolCall.of("get_incident", "Read incident INC0010001");

    private final AgentRegistry agents = mock(AgentRegistry.class);
    private final AgentSwitchesClient switches = mock(AgentSwitchesClient.class);
    private final AgentGovernanceClient governance = mock(AgentGovernanceClient.class);

    private final IncidentAgentKillSwitch killSwitch = new IncidentAgentKillSwitch(switches);
    private final ServiceNowToolRefusals refusals = new ServiceNowToolRefusals();
    private final ToolCallAuditTrail audit = new ToolCallAuditTrail(agents, governance);

    @Test
    void aSwitchThatCannotBeReadCountsAsOffAndOnlyTheHandOverWorksWhileOff() {
        when(switches.all()).thenReturn(Map.of("incident-agent", true));
        assertThat(killSwitch.isSwitchedOn("incident-agent")).isTrue();
        assertThat(killSwitch.isSwitchedOn("shopping-assistant")).isFalse();

        when(switches.all()).thenThrow(new IllegalStateException("commerce MCP server is down"));
        assertThat(killSwitch.isSwitchedOn("incident-agent")).isFalse();

        assertThat(killSwitch.worksWhileSwitchedOff("assign_to_team")).isTrue();
        assertThat(killSwitch.worksWhileSwitchedOff("resolve_incident")).isFalse();
    }

    @Test
    void refusalsTellTheAgentWhatToDoAndARuleIsDeniedWhileAnythingElseFailed() {
        assertThat(refusals.notPermitted("incident-agent", "get_incident").getMessage())
                .isEqualTo("Refused: Agent incident-agent is not permitted to call get_incident");
        assertThat(refusals.switchedOff("incident-agent").getMessage()).isEqualTo("Refused: Agent incident-agent is "
                + "switched off. Stop, and hand the incident to a team with assign_to_team.");
        assertThat(refusals.outcomeOf(new IncidentNotFoundException("INC0010001"))).isEqualTo(StoppedCallOutcome.DENIED);
        assertThat(refusals.outcomeOf(new IllegalStateException("ServiceNow is down")))
                .isEqualTo(StoppedCallOutcome.FAILED);
    }

    @Test
    void eachCallIsRecordedUnderTheAgentsOwnTokenAsAServiceNowAction() {
        when(agents.tokenOf("incident-agent")).thenReturn("agent-token");

        audit.notPermitted("incident-agent", READ);
        audit.switchedOff("incident-agent", READ);
        audit.succeeded("incident-agent", READ);
        audit.stopped("incident-agent", READ, StoppedCallOutcome.FAILED, "ServiceNow is down");

        verify(governance).recordToolCall("Bearer agent-token", new ToolCallReport(null, "servicenow:get_incident",
                ToolCallOutcome.DENIED, "Read incident INC0010001: tool not permitted for this agent", null));
        verify(governance).recordToolCall("Bearer agent-token", new ToolCallReport(null, "servicenow:get_incident",
                ToolCallOutcome.DENIED, "Read incident INC0010001: agent is switched off", null));
        verify(governance).recordToolCall("Bearer agent-token", new ToolCallReport(null, "servicenow:get_incident",
                ToolCallOutcome.SUCCEEDED, "Read incident INC0010001", null));
        verify(governance).recordToolCall("Bearer agent-token", new ToolCallReport(null, "servicenow:get_incident",
                ToolCallOutcome.FAILED, "Read incident INC0010001: ServiceNow is down", null));
    }

    @Test
    void anAuditTrailThatCannotBeReachedDoesNotStopTheCall() {
        when(agents.tokenOf("incident-agent")).thenReturn("agent-token");
        doThrow(new IllegalStateException("commerce MCP server is down"))
                .when(governance).recordToolCall(eq("Bearer agent-token"), any());

        assertThatCode(() -> audit.succeeded("incident-agent", READ)).doesNotThrowAnyException();
    }

    @Test
    void runsTheToolThroughTheSharedGovernedToolCall() {
        var calls = mock(GovernedToolCalls.class);
        when(calls.run(any(), eq(READ), any())).thenReturn("the incident");

        var result = new ToolGuard(calls).run(null, "get_incident", "Read incident INC0010001", agentId -> "not run here");

        assertThat(result).isEqualTo("the incident");
    }
}
