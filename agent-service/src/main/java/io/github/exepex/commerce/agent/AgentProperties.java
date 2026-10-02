package io.github.exepex.commerce.agent;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The agents' configuration, under {@code commerce}.
 *
 * @param agents the model, the MCP server, and each agent's settings
 * @param slack the Slack MCP server and the one channel agents may post in
 */
@ConfigurationProperties("commerce")
public record AgentProperties(Agents agents, Slack slack) {

    /**
     * @param model the Claude model every agent runs on
     * @param mcpUrl the commerce MCP server
     */
    public record Agents(String model, String mcpUrl, Agent shoppingAssistant, Agent orderExceptionsAgent) {}

    /**
     * @param token the bearer token the agent presents to the MCP server
     * @param effort how hard Claude thinks: low, medium, high, xhigh or max
     * @param tools the only MCP tools the agent is given
     */
    public record Agent(String token, String effort, List<String> tools) {}

    /**
     * @param mcpUrl the Slack MCP server; empty to run without Slack
     * @param apiKey the bearer token the Slack MCP server expects
     * @param channelId the channel agents post in
     * @param tools the only Slack tools agents are given
     */
    public record Slack(String mcpUrl, String apiKey, String channelId, List<String> tools) {

        public boolean isConfigured() {
            return mcpUrl != null && !mcpUrl.isBlank() && channelId != null && !channelId.isBlank();
        }
    }
}
