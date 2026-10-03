package io.github.exepex.commerce.mcpserver.security;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.mcpserver.exception.MissingAgentTokenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

/**
 * Knows the agents that may use this server: who a bearer token belongs to (from the configuration), and which of the
 * server's tools each may call (from its definition). An agent whose definition lists tools here but that has no
 * token, or a token for an agent that has no definition, stops the server at startup.
 */
public class AgentRegistry {

    private final Map<String, AgentToken> tokens;
    private final AgentDefinitions definitions;
    private final ServerTools serverTools;

    public AgentRegistry(McpServerProperties properties, AgentDefinitions definitions, ServerTools serverTools) {
        this.tokens = properties.agents() == null ? Map.of() : Map.copyOf(properties.agents());
        this.definitions = definitions;
        this.serverTools = serverTools;
        for (var definition : definitions.all()) {
            if (!serverTools.of(definition).isEmpty() && !tokens.containsKey(definition.id())) {
                throw new MissingAgentTokenException(definition.id());
            }
        }
        tokens.keySet().forEach(definitions::get);
    }

    /** The agent a presented token belongs to, compared in constant time so the comparison gives nothing away. */
    public Optional<String> agentWithToken(String token) {
        var presented = token.getBytes(StandardCharsets.UTF_8);
        return tokens.entrySet().stream()
                .filter(agent -> MessageDigest.isEqual(agent.getValue().token().getBytes(StandardCharsets.UTF_8),
                        presented))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** The agent's own token, for reporting what it did under its own name. */
    public String tokenOf(String agentId) {
        return tokens.get(agentId).token();
    }

    public boolean mayCall(String agentId, String tool) {
        return definitionOf(agentId).map(definition -> serverTools.of(definition).contains(tool)).orElse(false);
    }

    /** The definition of an agent this server knows; empty for any other id. */
    public Optional<AgentDefinition> definitionOf(String agentId) {
        return tokens.containsKey(agentId) ? Optional.of(definitions.get(agentId)) : Optional.empty();
    }
}
