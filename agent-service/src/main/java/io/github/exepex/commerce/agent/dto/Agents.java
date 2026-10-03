package io.github.exepex.commerce.agent.dto;

import io.github.exepex.commerce.agent.constants.AgentIds;
import io.github.exepex.commerce.agent.exception.UnknownAgentException;

/**
 * Where the commerce MCP server is and how each agent identifies itself to the MCP servers.
 *
 * @param mcpUrl the commerce MCP server
 * @param shoppingAssistant the shopping assistant's credentials
 * @param incidentAgent the incident agent's credentials
 */
public record Agents(String mcpUrl, Agent shoppingAssistant, Agent incidentAgent) {

    /** The bearer token the given agent presents to the MCP servers. */
    public String tokenOf(String agentId) {
        return switch (agentId) {
            case AgentIds.SHOPPING_ASSISTANT -> shoppingAssistant.token();
            case AgentIds.INCIDENT_AGENT -> incidentAgent.token();
            default -> throw new UnknownAgentException(agentId);
        };
    }
}
