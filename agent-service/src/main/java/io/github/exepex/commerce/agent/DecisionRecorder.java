package io.github.exepex.commerce.agent;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/** Writes each agent decision to the audit trail, with the model and the tokens it used. */
@Component
class DecisionRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecisionRecorder.class);

    private final GovernanceApi governance;
    private final AgentProperties properties;

    DecisionRecorder(GovernanceApi governance, AgentProperties properties) {
        this.governance = governance;
        this.properties = properties;
    }

    void record(String agentId, UUID orderId, String summary, String reasoning, ChatResponse response, Duration took) {
        Usage usage = response == null ? null : response.getMetadata().getUsage();
        GovernanceApi.Decision decision = new GovernanceApi.Decision(orderId, summary, reasoning,
                properties.agents().model(),
                usage == null ? null : usage.getPromptTokens().longValue(),
                usage == null ? null : usage.getCompletionTokens().longValue(),
                took.toMillis());
        try {
            governance.recordDecision("Bearer " + tokenOf(agentId), decision);
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Could not record {}'s decision in the audit trail: {}", agentId, summary, unavailable);
        }
    }

    private String tokenOf(String agentId) {
        return AgentSwitchboard.SHOPPING_ASSISTANT.equals(agentId)
                ? properties.agents().shoppingAssistant().token()
                : properties.agents().orderExceptionsAgent().token();
    }
}
