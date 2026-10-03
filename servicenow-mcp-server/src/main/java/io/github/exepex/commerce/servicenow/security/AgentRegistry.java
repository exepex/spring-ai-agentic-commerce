package io.github.exepex.commerce.servicenow.security;

import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.servicenow.AgentTokens;
import io.github.exepex.commerce.servicenow.dto.Agent;
import io.github.exepex.commerce.servicenow.exception.MissingAgentTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Knows the agents that may use ServiceNow: those whose definition lists {@code servicenow} tools. Each needs a token;
 * one without fails at startup.
 */
@Component
public class AgentRegistry {

    private final Map<String, Agent> tokens;
    private final AgentDefinitions definitions;

    AgentRegistry(AgentTokens agentTokens, AgentDefinitions definitions) {
        this.tokens = Map.copyOf(agentTokens.agents());
        this.definitions = definitions;
        for (var definition : definitions.all()) {
            if (!definition.servicenowTools().isEmpty() && !tokens.containsKey(definition.id())) {
                throw new MissingAgentTokenException(definition.id());
            }
        }
    }

    Optional<String> agentWithToken(String token) {
        var presented = token.getBytes(StandardCharsets.UTF_8);
        return tokens.entrySet().stream()
                .filter(agent -> MessageDigest.isEqual(agent.getValue().token().getBytes(StandardCharsets.UTF_8), presented))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public boolean mayCall(String agentId, String tool) {
        return tokens.containsKey(agentId) && definitions.get(agentId).servicenowTools().contains(tool);
    }

    /** The agent's own token, used to record its tool calls in the governance audit trail as that agent. */
    public String tokenOf(String agentId) {
        return tokens.get(agentId).token();
    }
}
