package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agent.constants.AgentIds;
import io.github.exepex.commerce.agent.constants.AuditTexts;
import io.github.exepex.commerce.agent.constants.CustomerReplies;
import io.github.exepex.commerce.agent.constants.McpValues;
import io.github.exepex.commerce.agent.constants.Prompts;
import io.github.exepex.commerce.agent.dto.Reply;
import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.platform.logging.LogValues;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * The customer-facing agent: answers questions, finds products, proposes orders, and handles cancellations. It can
 * propose an order but never place one; only the customer's own "Confirm and pay" does that.
 */
@Slf4j
@Service
public class ShoppingAssistant {

    private static final int MAX_CONVERSATIONS = 1_000;
    /** How much of each answer the audit trail shows. */
    private static final int MAX_SUMMARY_LENGTH = 160;

    private final ChatClient chatClient;
    private final McpToolboxes toolboxes;
    private final AgentSwitchboard switchboard;
    private final DecisionRecorder decisions;
    private final AgentDefinition definition;

    ShoppingAssistant(ChatModel chatModel, McpToolboxes toolboxes, AgentSwitchboard switchboard,
            DecisionRecorder decisions, AgentDefinitions definitions) {
        this.definition = definitions.get(AgentIds.SHOPPING_ASSISTANT);
        var memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new RecentConversations(MAX_CONVERSATIONS))
                .maxMessages(30)
                .build();
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(ClaudeOptions.forAgent(definition.model(), definition.effort()))
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
        this.toolboxes = toolboxes;
        this.switchboard = switchboard;
        this.decisions = decisions;
    }

    public Reply chat(String conversationId, String customerEmail, String message) {
        boolean enabled;
        try {
            enabled = switchboard.isEnabled(AgentIds.SHOPPING_ASSISTANT);
        } catch (RuntimeException unreachable) {
            log.error("Could not read the shopping assistant's kill switch", unreachable);
            return new Reply(CustomerReplies.TRY_AGAIN, List.of());
        }
        if (!enabled) {
            return new Reply(CustomerReplies.ASSISTANT_SWITCHED_OFF, List.of());
        }
        var run = new ToolRun(customerEmail, definition.toolCallBudget());
        var started = Instant.now();
        try {
            var response = chatClient.prompt()
                    .system(definition.systemPrompt(null) + Prompts.SIGNED_IN_CUSTOMER.formatted(customerEmail))
                    .user(message)
                    .toolCallbacks(toolboxes.shoppingAssistantTools())
                    .toolContext(Map.of(McpValues.TOOL_RUN_CONTEXT_KEY, run))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .chatResponse();
            var text = ClaudeReply.textOf(response);
            decisions.record(AgentIds.SHOPPING_ASSISTANT, null,
                    AuditTexts.ANSWERED.formatted(customerEmail,
                            AgentTexts.abbreviate(text, MAX_SUMMARY_LENGTH, AuditTexts.ELLIPSIS)),
                    AuditTexts.CUSTOMER_ASKED.formatted(message), response, Duration.between(started, Instant.now()));
            return new Reply(text, run.proposals());
        } catch (RuntimeException failure) {
            log.error("The shopping assistant failed to answer {}", LogValues.safe(customerEmail), failure);
            return new Reply(CustomerReplies.TRY_AGAIN, run.proposals());
        }
    }
}
