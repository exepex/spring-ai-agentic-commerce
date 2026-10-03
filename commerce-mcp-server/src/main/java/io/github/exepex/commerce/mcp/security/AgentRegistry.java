package io.github.exepex.commerce.mcp.security;

import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.mcp.GovernanceProperties;
import io.github.exepex.commerce.mcp.dto.Agent;
import io.github.exepex.commerce.mcp.exception.MissingAgentTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Knows every agent: who a bearer token belongs to (from the configuration), and which tools that agent may call and
 * whether it is customer-scoped (from its definition). An agent without both fails at startup.
 */
@Component
public class AgentRegistry {

    private final Map<String, Agent> agents;
    private final AgentDefinitions definitions;

    AgentRegistry(GovernanceProperties properties, AgentDefinitions definitions) {
        this.agents = Map.copyOf(properties.agents());
        this.definitions = definitions;
        for (var definition : definitions.all()) {
            if (!agents.containsKey(definition.id())) {
                throw new MissingAgentTokenException(definition.id());
            }
        }
        agents.keySet().forEach(definitions::get);
    }

    Optional<String> agentWithToken(String token) {
        var presented = token.getBytes(StandardCharsets.UTF_8);
        return agents.entrySet().stream()
                .filter(agent -> MessageDigest.isEqual(agent.getValue().token().getBytes(StandardCharsets.UTF_8), presented))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public boolean mayCall(String agentId, String tool) {
        return agents.containsKey(agentId) && definitions.get(agentId).commerceTools().contains(tool);
    }

    public boolean isCustomerScoped(String agentId) {
        return agents.containsKey(agentId) && definitions.get(agentId).customerScoped();
    }
}
