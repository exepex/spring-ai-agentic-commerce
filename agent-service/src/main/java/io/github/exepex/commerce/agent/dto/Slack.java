package io.github.exepex.commerce.agent.dto;

/**
 * The Slack MCP server and the one channel agents may post in.
 *
 * @param mcpUrl the Slack MCP server; empty to run without Slack
 * @param apiKey the bearer token the Slack MCP server expects
 * @param channelId the channel agents post in
 */
public record Slack(String mcpUrl, String apiKey, String channelId) {

    public boolean isConfigured() {
        return mcpUrl != null && !mcpUrl.isBlank() && channelId != null && !channelId.isBlank();
    }
}
