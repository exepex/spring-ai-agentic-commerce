package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agents.AgentDefinitions;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/** Writes each agent decision to the audit trail, with the model and the tokens it used. */
@Slf4j
@Component
@RequiredArgsConstructor
class DecisionRecorder {

    private final GovernanceApi governance;
    private final AgentProperties properties;
    private final AgentDefinitions definitions;

    void record(String agentId, UUID orderId, String summary, String reasoning, ChatResponse response, Duration took) {
        Usage usage = response == null ? null : response.getMetadata().getUsage();
        GovernanceApi.Decision decision = new GovernanceApi.Decision(orderId, summary, reasoning,
                definitions.get(agentId).model(),
                usage == null ? null : usage.getPromptTokens().longValue(),
                usage == null ? null : usage.getCompletionTokens().longValue(),
                took.toMillis());
        try {
            governance.recordDecision("Bearer " + properties.agents().tokenOf(agentId), decision);
        } catch (RuntimeException unavailable) {
            log.warn("Could not record {}'s decision in the audit trail: {}", LogValues.safe(agentId), LogValues.safe(summary),
                    unavailable);
        }
    }
}
