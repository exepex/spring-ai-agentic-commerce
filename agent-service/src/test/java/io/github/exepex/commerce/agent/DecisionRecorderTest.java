package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.exepex.commerce.agent.constants.AgentIds;
import io.github.exepex.commerce.agent.dto.Agent;
import io.github.exepex.commerce.agent.dto.Agents;
import io.github.exepex.commerce.agent.dto.ServiceNow;
import io.github.exepex.commerce.agent.dto.Slack;
import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.governance.api.client.AgentGovernanceClient;
import io.github.exepex.commerce.governance.api.dto.AgentDecision;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionRecorderTest {

    private static final AgentDefinitions DEFINITIONS = AgentDefinitions.load();
    private static final AgentProperties PROPERTIES = new AgentProperties(
            new Agents("http://localhost:8085",
                    new Agent("assistant-token"), new Agent("incident-token"), Duration.ofMinutes(15),
                    Duration.ofDays(1)),
            new Slack("", "", ""), new ServiceNow(""));

    private final AgentGovernanceClient governance = mock(AgentGovernanceClient.class);
    private final DecisionRecorder recorder = new DecisionRecorder(governance, PROPERTIES, DEFINITIONS);

    @Test
    void eachAgentRecordsItsDecisionUnderItsOwnToken() {
        var orderId = UUID.randomUUID();

        recorder.record(AgentIds.INCIDENT_AGENT, orderId, "Incident INC0010001: refunded", "Triggered by", null,
                Duration.ofMillis(1200));
        recorder.record(AgentIds.SHOPPING_ASSISTANT, null, "Answered ann@example.com: hello", "Customer asked", null,
                Duration.ofMillis(300));

        verify(governance).recordDecision("Bearer incident-token", new AgentDecision(orderId,
                "Incident INC0010001: refunded", "Triggered by",
                DEFINITIONS.get(AgentIds.INCIDENT_AGENT).model(), null, null, 1200L));
        verify(governance).recordDecision("Bearer assistant-token", new AgentDecision(null,
                "Answered ann@example.com: hello", "Customer asked",
                DEFINITIONS.get(AgentIds.SHOPPING_ASSISTANT).model(), null, null, 300L));
    }

    @Test
    void anAuditTrailThatCannotBeReachedDoesNotFailTheRun() {
        doThrow(new IllegalStateException("down")).when(governance).recordDecision(anyString(), any());

        assertThatCode(() -> recorder.record(AgentIds.INCIDENT_AGENT, null, "summary", "reasoning", null,
                Duration.ZERO)).doesNotThrowAnyException();
    }
}
