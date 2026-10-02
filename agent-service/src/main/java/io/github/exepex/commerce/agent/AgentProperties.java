package io.github.exepex.commerce.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the agents connect and with which secrets, under {@code commerce}. What each agent is (model, effort, tools,
 * budget, prompt) comes from its definition in the agent-definitions module.
 *
 * @param agents the commerce MCP server and each agent's credentials
 * @param slack the Slack MCP server and the one channel agents may post in
 */
@ConfigurationProperties("commerce")
public record AgentProperties(Agents agents, Slack slack) {

    /**
     * @param mcpUrl the commerce MCP server
     * @param shoppingAssistant the shopping assistant's credentials
     * @param orderExceptionsAgent the order-exceptions agent's credentials
     */
    public record Agents(String mcpUrl, Agent shoppingAssistant, Agent orderExceptionsAgent) {

        /** The bearer token the given agent presents to the MCP server. */
        public String tokenOf(String agentId) {
            return AgentSwitchboard.SHOPPING_ASSISTANT.equals(agentId)
                    ? shoppingAssistant.token()
                    : orderExceptionsAgent.token();
        }
    }

    /** @param token the bearer token the agent presents to the MCP server */
    public record Agent(String token) {}

    /**
     * @param mcpUrl the Slack MCP server; empty to run without Slack
     * @param apiKey the bearer token the Slack MCP server expects
     * @param channelId the channel agents post in
     */
    public record Slack(String mcpUrl, String apiKey, String channelId) {

        public boolean isConfigured() {
            return mcpUrl != null && !mcpUrl.isBlank() && channelId != null && !channelId.isBlank();
        }
    }
}
