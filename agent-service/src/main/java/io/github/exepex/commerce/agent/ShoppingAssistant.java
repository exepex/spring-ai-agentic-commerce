package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * The customer-facing agent: answers questions, finds products, proposes orders, and handles cancellations. It can
 * propose an order but never place one; only the customer's own "Confirm and pay" does that.
 */
@Service
public class ShoppingAssistant {

    public record Reply(String text, List<String> proposals) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(ShoppingAssistant.class);
    private static final int MAX_CONVERSATIONS = 1_000;


    private final ChatClient chatClient;
    private final McpToolboxes toolboxes;
    private final AgentSwitchboard switchboard;
    private final DecisionRecorder decisions;
    private final AgentDefinition definition;

    ShoppingAssistant(ChatModel chatModel, McpToolboxes toolboxes, AgentSwitchboard switchboard,
            DecisionRecorder decisions, AgentDefinitions definitions) {
        this.definition = definitions.get(AgentSwitchboard.SHOPPING_ASSISTANT);
        ChatMemory memory = MessageWindowChatMemory.builder()
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
        if (!switchboard.isEnabled(AgentSwitchboard.SHOPPING_ASSISTANT)) {
            return new Reply("The shopping assistant is switched off right now. Please try again later.", List.of());
        }
        ToolRun run = new ToolRun(customerEmail, definition.toolCallBudget());
        Instant started = Instant.now();
        try {
            ChatResponse response = chatClient.prompt()
                    .system(definition.systemPrompt(null) + "\n\nThe signed-in customer is " + customerEmail + ".")
                    .user(message)
                    .toolCallbacks(toolboxes.shoppingAssistantTools())
                    .toolContext(Map.of(ToolRun.CONTEXT_KEY, run))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .chatResponse();
            String text = ClaudeReply.textOf(response);
            decisions.record(AgentSwitchboard.SHOPPING_ASSISTANT, null,
                    "Answered " + customerEmail + ": " + abbreviate(text), "Customer asked: " + message, response,
                    Duration.between(started, Instant.now()));
            return new Reply(text, run.proposals());
        } catch (RuntimeException failure) {
            LOGGER.error("The shopping assistant failed to answer {}", customerEmail, failure);
            return new Reply("Sorry, I could not answer that just now. Please try again in a moment.", run.proposals());
        }
    }

    private static String abbreviate(String text) {
        return text == null || text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }
}
