package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.exepex.commerce.agents.AgentDefinitions;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

class OrderExceptionsAgentTest {

    static final AgentProperties PROPERTIES = new AgentProperties(
            new AgentProperties.Agents("http://localhost:8085", new AgentProperties.Agent("token"),
                    new AgentProperties.Agent("token"), new AgentProperties.Agent("token")),
            new AgentProperties.Slack("", "", ""), new AgentProperties.ServiceNow("http://localhost:8087"));
    private static final AgentDefinitions DEFINITIONS = AgentDefinitions.load();

    @Test
    void anAgentThatFinishesWithoutDealingWithTheOrderHandsItToAPerson() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("I looked at it.")))));
        McpToolboxes toolboxes = mock(McpToolboxes.class);
        when(toolboxes.callAsOrderExceptionsAgent(eq("escalate_to_human"), any()))
                .thenReturn(McpSchema.CallToolResult.builder().addTextContent("{}").isError(false).build());
        OrderExceptionsAgent agent = new OrderExceptionsAgent(model, toolboxes, switchboard(true),
                mock(DecisionRecorder.class), DEFINITIONS, PROPERTIES);

        agent.handleStockOut(UUID.randomUUID(), "{}");

        verify(toolboxes).callAsOrderExceptionsAgent(eq("escalate_to_human"), argThat(arguments ->
                arguments.get("summary").toString().contains("finished without cancelling")));
    }

    @Test
    void aHandOffTheMcpServerRefusesCountsAsFailed() {
        McpToolboxes toolboxes = mock(McpToolboxes.class);
        when(toolboxes.callAsOrderExceptionsAgent(eq("escalate_to_human"), any()))
                .thenReturn(McpSchema.CallToolResult.builder().addTextContent("database down").isError(true).build());
        OrderExceptionsAgent agent = new OrderExceptionsAgent(mock(ChatModel.class), toolboxes, switchboard(false),
                mock(DecisionRecorder.class), DEFINITIONS, PROPERTIES);

        assertThatThrownBy(() -> agent.handleStockOut(UUID.randomUUID(), "{}"))
                .isInstanceOf(HandOffFailedException.class);
    }

    @Test
    void whenNotEvenTheHandOffToAHumanGoesThroughTheStockOutFailsSoKafkaDeliversItAgain() {
        McpToolboxes toolboxes = mock(McpToolboxes.class);
        when(toolboxes.callAsOrderExceptionsAgent(eq("escalate_to_human"), any()))
                .thenThrow(new IllegalStateException("MCP server unreachable"));
        OrderExceptionsAgent agent = new OrderExceptionsAgent(mock(ChatModel.class), toolboxes, switchboard(false),
                mock(DecisionRecorder.class), DEFINITIONS, PROPERTIES);

        assertThatThrownBy(() -> agent.handleStockOut(UUID.randomUUID(), "{}"))
                .isInstanceOf(HandOffFailedException.class)
                .hasMessageContaining("to a human");
    }

    @Test
    void whenTheKillSwitchCannotBeReadTheStockOutFailsSoKafkaDeliversItAgain() {
        AgentSwitchesApi unreachable = mock(AgentSwitchesApi.class);
        when(unreachable.all()).thenThrow(new IllegalStateException("MCP server unreachable"));
        ChatModel model = mock(ChatModel.class);
        McpToolboxes toolboxes = mock(McpToolboxes.class);
        OrderExceptionsAgent agent = new OrderExceptionsAgent(model, toolboxes, new AgentSwitchboard(unreachable),
                mock(DecisionRecorder.class), DEFINITIONS, PROPERTIES);

        assertThatThrownBy(() -> agent.handleStockOut(UUID.randomUUID(), "{}"))
                .isInstanceOf(HandOffFailedException.class)
                .hasMessageContaining("kill switch");
        verifyNoInteractions(model, toolboxes);
    }

    private static AgentSwitchboard switchboard(boolean exceptionsAgentOn) {
        AgentSwitchesApi switches = mock(AgentSwitchesApi.class);
        when(switches.all()).thenReturn(Map.of(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT, exceptionsAgentOn));
        return new AgentSwitchboard(switches);
    }
}
