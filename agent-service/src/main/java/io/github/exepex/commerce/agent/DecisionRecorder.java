package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.governance.api.client.AgentGovernanceClient;
import io.github.exepex.commerce.governance.api.dto.AgentDecision;
import io.github.exepex.commerce.platform.logging.LogValues;
import io.github.exepex.commerce.platform.security.BearerTokens;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/** Writes each agent decision to the audit trail, with the model and the tokens it used. */
@Slf4j
@Component
@RequiredArgsConstructor
class DecisionRecorder {

    private final AgentGovernanceClient governance;
    private final AgentProperties properties;
    private final AgentDefinitions definitions;

    void record(String agentId, UUID orderId, String summary, String reasoning, ChatResponse response, Duration took) {
        var usage = response == null ? null : response.getMetadata().getUsage();
        var decision = new AgentDecision(orderId, summary, reasoning,
                definitions.get(agentId).model(),
                usage == null ? null : usage.getPromptTokens().longValue(),
                usage == null ? null : usage.getCompletionTokens().longValue(),
                took.toMillis());
        try {
            governance.recordDecision(BearerTokens.authorization(properties.agents().tokenOf(agentId)), decision);
        } catch (RuntimeException unavailable) {
            log.warn("Could not record {}'s decision in the audit trail: {}", LogValues.safe(agentId), LogValues.safe(summary),
                    unavailable);
        }
    }
}
