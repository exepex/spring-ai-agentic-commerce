package io.github.exepex.commerce.agent.dto;

/** @param mcpUrl the ServiceNow MCP server; empty to run without ServiceNow */
public record ServiceNow(String mcpUrl) {

    public boolean isConfigured() {
        return mcpUrl != null && !mcpUrl.isBlank();
    }
}
