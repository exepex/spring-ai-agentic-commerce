package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class OrderExceptionsAgentTest {

    private static final AgentProperties PROPERTIES = new AgentProperties(
            new AgentProperties.Agents("claude-opus-5-5", "http://localhost:8085",
                    new AgentProperties.Agent("token", "medium", List.of()),
                    new AgentProperties.Agent("token", "high", List.of())),
            new AgentProperties.Slack("", "", "", List.of()));

    @Test
    void anAgentThatFinishesWithoutDealingWithTheOrderHandsItToAPerson() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("I looked at it.")))));
        McpToolboxes toolboxes = mock(McpToolboxes.class);
        when(toolboxes.callAsOrderExceptionsAgent(eq("escalate_to_human"), any()))
                .thenReturn(McpSchema.CallToolResult.builder().addTextContent("{}").isError(false).build());
        OrderExceptionsAgent agent = new OrderExceptionsAgent(model, toolboxes, new AgentSwitchboard(),
                mock(DecisionRecorder.class), PROPERTIES);

        agent.handleStockOut(UUID.randomUUID(), "{}");

        verify(toolboxes).callAsOrderExceptionsAgent(eq("escalate_to_human"), any());
    }

    @Test
    void aHandOffTheMcpServerRefusesCountsAsFailed() {
        McpToolboxes toolboxes = mock(McpToolboxes.class);
        when(toolboxes.callAsOrderExceptionsAgent(eq("escalate_to_human"), any()))
                .thenReturn(McpSchema.CallToolResult.builder().addTextContent("database down").isError(true).build());
        AgentSwitchboard switchboard = new AgentSwitchboard();
        switchboard.set(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT, false);
        OrderExceptionsAgent agent = new OrderExceptionsAgent(mock(ChatModel.class), toolboxes, switchboard,
                mock(DecisionRecorder.class), PROPERTIES);

        assertThatThrownBy(() -> agent.handleStockOut(UUID.randomUUID(), "{}"))
                .isInstanceOf(OrderExceptionsAgent.HandOffFailedException.class);
    }

    @Test
    void whenNotEvenTheHandOffToAHumanGoesThroughTheStockOutFailsSoKafkaDeliversItAgain() {
        McpToolboxes toolboxes = mock(McpToolboxes.class);
        when(toolboxes.callAsOrderExceptionsAgent(eq("escalate_to_human"), any()))
                .thenThrow(new IllegalStateException("MCP server unreachable"));
        AgentSwitchboard switchboard = new AgentSwitchboard();
        switchboard.set(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT, false);
        OrderExceptionsAgent agent = new OrderExceptionsAgent(mock(ChatModel.class), toolboxes, switchboard,
                mock(DecisionRecorder.class), PROPERTIES);

        assertThatThrownBy(() -> agent.handleStockOut(UUID.randomUUID(), "{}"))
                .isInstanceOf(OrderExceptionsAgent.HandOffFailedException.class)
                .hasMessageContaining("to a human");
    }
}
