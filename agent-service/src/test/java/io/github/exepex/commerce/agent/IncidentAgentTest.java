package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.exepex.commerce.agent.constants.AgentIds;
import io.github.exepex.commerce.agent.dto.Agent;
import io.github.exepex.commerce.agent.dto.Agents;
import io.github.exepex.commerce.agent.dto.ServiceNow;
import io.github.exepex.commerce.agent.dto.Slack;
import io.github.exepex.commerce.agent.exception.HandOffRejectedException;
import io.github.exepex.commerce.agent.exception.HandOffUnreachableException;
import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.governance.api.client.AgentSwitchesClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

class IncidentAgentTest {

    private static final AgentDefinitions DEFINITIONS = AgentDefinitions.load();
    private static final AgentProperties PROPERTIES = new AgentProperties(
            new Agents("http://localhost:8085", new Agent("token"),
                    new Agent("token"), Duration.ofMinutes(15)),
            new Slack("", "", ""), new ServiceNow("http://localhost:8087"));
    private static final String INCIDENT = "INC0010001";

    private final McpToolboxes toolboxes = mock(McpToolboxes.class);

    @Test
    void aSwitchedOffAgentHandsTheIncidentToATeamWithoutCallingTheModel() {
        var model = mock(ChatModel.class);
        when(toolboxes.callAsIncidentAgent(eq("assign_to_team"), any())).thenReturn(result("Assigned", false));

        agent(model, false).handleIncident(INCIDENT, "", "{}");

        verify(toolboxes).callAsIncidentAgent(eq("assign_to_team"),
                argThat(arguments -> INCIDENT.equals(arguments.get("number"))));
        verifyNoInteractions(model);
    }

    @Test
    void aRunThatNeitherResolvesNorHandsOverGoesToATeam() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("I looked at it.")))));
        when(toolboxes.callAsIncidentAgent(eq("assign_to_team"), any())).thenReturn(result("Assigned", false));

        agent(model, true).handleIncident(INCIDENT, "", "{}");

        verify(toolboxes).callAsIncidentAgent(eq("assign_to_team"), argThat(arguments ->
                arguments.get("note").toString().contains("finished without resolving")));
    }

    @Test
    void aRefusedHandOffMeansTheIncidentIsNoLongerTheAgentsAndEndsTheWork() {
        when(toolboxes.callAsIncidentAgent(eq("assign_to_team"), any()))
                .thenReturn(result("Refused: Incident INC0010001 is not yours to change", true));

        assertThatCode(() -> agent(mock(ChatModel.class), false).handleIncident(INCIDENT, "", "{}")).doesNotThrowAnyException();
    }

    @Test
    void aHandOffServiceNowDidNotTakeIsDeliveredAgain() {
        when(toolboxes.callAsIncidentAgent(eq("assign_to_team"), any())).thenReturn(result("502 Bad Gateway", true));
        assertThatThrownBy(() -> agent(mock(ChatModel.class), false).handleIncident(INCIDENT, "", "{}"))
                .isInstanceOf(HandOffRejectedException.class);

        when(toolboxes.callAsIncidentAgent(eq("assign_to_team"), any())).thenThrow(new IllegalStateException("down"));
        assertThatThrownBy(() -> agent(mock(ChatModel.class), false).handleIncident(INCIDENT, "", "{}"))
                .isInstanceOf(HandOffUnreachableException.class);
    }

    @Test
    void onlyResolvingOrHandingOverThisIncidentFinishesIt() {
        var run = new ToolRun(null, 10);
        run.recordSuccess("add_work_note", "{\"number\": \"" + INCIDENT + "\"}", "{}");
        run.recordSuccess("resolve_incident", "{\"number\": \"INC0099999\"}", "{}");
        assertThat(IncidentAgent.isFinished(INCIDENT, run)).isFalse();

        run.recordSuccess("assign_to_team", "{\"number\": \"" + INCIDENT + "\", \"team\": \"payments\"}", "{}");
        assertThat(IncidentAgent.isFinished(INCIDENT, run)).isTrue();
    }

    @Test
    void anOrderMayOnlyChangeWhileTheIncidentIsStillTheAgentsAndStillLinksToIt() {
        var orderA = "0b6f2a3e-5d1c-4c1e-9a7b-2f1d3c4b5a69";
        var orderB = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
        var agent = agent(mock(ChatModel.class), true);

        when(toolboxes.callAsIncidentAgent(eq("get_incident"), any()))
                .thenReturn(result("{\"number\": \"INC0010001\", \"linkedOrderId\": \"" + orderA + "\"}", false));
        assertThat(agent.stillLinksTo(INCIDENT, orderA.toUpperCase())).isTrue();

        // The service desk corrected the incident's Correlation ID from order A to order B during the run.
        when(toolboxes.callAsIncidentAgent(eq("get_incident"), any()))
                .thenReturn(result("{\"number\": \"INC0010001\", \"linkedOrderId\": \"" + orderB + "\"}", false));
        assertThat(agent.stillLinksTo(INCIDENT, orderA)).isFalse();

        when(toolboxes.callAsIncidentAgent(eq("get_incident"), any()))
                .thenReturn(result("Refused: Incident INC0010001 is not yours to change", true));
        assertThat(agent.stillLinksTo(INCIDENT, orderB)).isFalse();
    }

    private IncidentAgent agent(ChatModel model, boolean switchedOn) {
        var switches = mock(AgentSwitchesClient.class);
        when(switches.all()).thenReturn(Map.of(AgentIds.INCIDENT_AGENT, switchedOn));
        return new IncidentAgent(model, toolboxes, new AgentSwitchboard(switches), mock(DecisionRecorder.class),
                DEFINITIONS, PROPERTIES);
    }

    private static McpSchema.CallToolResult result(String text, boolean error) {
        return McpSchema.CallToolResult.builder().addTextContent(text).isError(error).build();
    }
}
