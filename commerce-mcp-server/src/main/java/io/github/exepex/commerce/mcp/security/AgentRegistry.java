package io.github.exepex.commerce.mcp.security;

import io.github.exepex.commerce.mcp.GovernanceProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Knows every agent: who a bearer token belongs to, and which tools that agent may call. */
@Component
public class AgentRegistry {

    private final Map<String, GovernanceProperties.Agent> agents;

    AgentRegistry(GovernanceProperties properties) {
        this.agents = Map.copyOf(properties.agents());
    }

    Optional<String> agentWithToken(String token) {
        byte[] presented = token.getBytes(StandardCharsets.UTF_8);
        return agents.entrySet().stream()
                .filter(agent -> MessageDigest.isEqual(agent.getValue().token().getBytes(StandardCharsets.UTF_8), presented))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public boolean mayCall(String agentId, String tool) {
        GovernanceProperties.Agent agent = agents.get(agentId);
        return agent != null && agent.tools().contains(tool);
    }

    public boolean isCustomerScoped(String agentId) {
        GovernanceProperties.Agent agent = agents.get(agentId);
        return agent != null && agent.customerScoped();
    }
}
