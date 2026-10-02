package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Handles orders that can no longer be fulfilled because stock ran out after they were placed: it cancels, refunds
 * within its limit, tells the customer, and reports to the operations channel. When it is switched off or fails,
 * the order goes to a human instead.
 */
@Service
public class OrderExceptionsAgent {

    /** Neither the agent nor a person got the order: Kafka keeps delivering the stock-out until one does. */
    static final class HandOffFailedException extends IllegalStateException {

        HandOffFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderExceptionsAgent.class);

    private final ChatClient chatClient;
    private final McpToolboxes toolboxes;
    private final AgentSwitchboard switchboard;
    private final DecisionRecorder decisions;
    private final AgentDefinition definition;
    private final String systemPrompt;

    OrderExceptionsAgent(ChatModel chatModel, McpToolboxes toolboxes, AgentSwitchboard switchboard,
            DecisionRecorder decisions, AgentDefinitions definitions, AgentProperties properties) {
        this.definition = definitions.get(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT);
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(ClaudeOptions.forAgent(definition.model(), definition.effort()))
                .build();
        this.toolboxes = toolboxes;
        this.switchboard = switchboard;
        this.decisions = decisions;
        this.systemPrompt = definition.systemPrompt(properties.slack().isConfigured() ? properties.slack().channelId() : null);
    }

    public void handleStockOut(UUID orderId, String stockOutEvent) {
        boolean enabled;
        try {
            enabled = switchboard.isEnabled(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT);
        } catch (RuntimeException unreachable) {
            throw new HandOffFailedException("Could not read the kill switch, so order " + orderId
                    + " was neither handled nor handed to a human", unreachable);
        }
        if (!enabled) {
            handToHuman(orderId, "Stock-out: this order can no longer be fulfilled as placed. The order-exceptions agent "
                    + "is switched off, so a person must decide whether to cancel and refund it.");
            return;
        }
        Instant started = Instant.now();
        ToolRun run = new ToolRun(null, definition.toolCallBudget());
        ChatResponse response;
        try {
            response = chatClient.prompt()
                    .system(systemPrompt)
                    .user("Order " + orderId + " is affected by this stock-out event:\n" + stockOutEvent)
                    .toolCallbacks(toolboxes.orderExceptionsAgentTools())
                    .toolContext(Map.of(ToolRun.CONTEXT_KEY, run))
                    .call()
                    .chatResponse();
        } catch (RuntimeException failure) {
            LOGGER.error("The order-exceptions agent failed on order {}", orderId, failure);
            handToHuman(orderId, "Stock-out: this order can no longer be fulfilled as placed. The order-exceptions agent "
                    + "failed while handling it (" + failure.getMessage() + "), so a person must finish: check the order, "
                    + "cancel and refund it if that has not happened yet.");
            return;
        }
        String summary = ClaudeReply.textOf(response);
        decisions.record(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT, orderId, summary,
                "Triggered by stock-out event: " + stockOutEvent, response, Duration.between(started, Instant.now()));
        if (!StockOutSettlement.isSettled(orderId, run.succeeded())) {
            handToHuman(orderId, "Stock-out: the order-exceptions agent finished without cancelling and refunding this "
                    + "order or handing it over, so a person must finish it. The agent said: " + summary);
        }
    }

    /**
     * If not even the hand-off reaches the MCP server, the failure is passed on: Kafka then delivers the stock-out
     * again, so the order is never left without an agent or a person handling it.
     */
    private void handToHuman(UUID orderId, String summary) {
        McpSchema.CallToolResult result;
        try {
            result = toolboxes.callAsOrderExceptionsAgent("escalate_to_human",
                    Map.of("orderId", orderId.toString(), "summary", summary));
        } catch (RuntimeException unavailable) {
            throw new HandOffFailedException("Could not hand order " + orderId + " to a human: " + summary, unavailable);
        }
        if (Boolean.TRUE.equals(result.isError())) {
            throw new HandOffFailedException("The MCP server refused to hand order " + orderId + " to a human: "
                    + result.content(), null);
        }
    }
}
