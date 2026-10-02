package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class OrderExceptionsAgentTest {

    @Test
    void whenNotEvenTheHandOffToAHumanGoesThroughTheStockOutFailsSoKafkaDeliversItAgain() {
        McpToolboxes toolboxes = mock(McpToolboxes.class);
        when(toolboxes.callAsOrderExceptionsAgent(eq("escalate_to_human"), any()))
                .thenThrow(new IllegalStateException("MCP server unreachable"));
        AgentSwitchboard switchboard = new AgentSwitchboard();
        switchboard.set(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT, false);
        AgentProperties properties = new AgentProperties(
                new AgentProperties.Agents("claude-opus-5-5", "http://localhost:8085",
                        new AgentProperties.Agent("token", "medium", List.of()),
                        new AgentProperties.Agent("token", "high", List.of())),
                new AgentProperties.Slack("", "", "", List.of()));
        OrderExceptionsAgent agent = new OrderExceptionsAgent(mock(ChatModel.class), toolboxes, switchboard,
                mock(DecisionRecorder.class), properties);

        assertThatThrownBy(() -> agent.handleStockOut(UUID.randomUUID(), "{}"))
                .isInstanceOf(OrderExceptionsAgent.HandOffFailedException.class)
                .hasMessageContaining("to a human");
    }
}
