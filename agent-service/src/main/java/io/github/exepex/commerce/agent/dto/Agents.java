package io.github.exepex.commerce.agent.dto;

import io.github.exepex.commerce.agent.constants.AgentIds;
import io.github.exepex.commerce.agent.exception.UnknownAgentException;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the commerce MCP server is and how each agent identifies itself to the MCP servers.
 *
 * @param mcpUrl the commerce MCP server
 * @param shoppingAssistant the shopping assistant's credentials
 * @param incidentAgent the incident agent's credentials
 * @param incidentRunLimit how long one incident run may act: after it, every tool call of the run is refused
 */
public record Agents(String mcpUrl, Agent shoppingAssistant, Agent incidentAgent,
        @DefaultValue("15m") Duration incidentRunLimit) {

    /** The bearer token the given agent presents to the MCP servers. */
    public String tokenOf(String agentId) {
        return switch (agentId) {
            case AgentIds.SHOPPING_ASSISTANT -> shoppingAssistant.token();
            case AgentIds.INCIDENT_AGENT -> incidentAgent.token();
            default -> throw new UnknownAgentException(agentId);
        };
    }
}
