package io.github.exepex.commerce.mcp.security;

import io.modelcontextprotocol.common.McpTransportContext;

/** Reads the authenticated agent's id from a tool call's transport context. */
public final class CallingAgent {

    private CallingAgent() {}

    public static String of(McpTransportContext context) {
        Object agentId = context.get(McpTransportConfiguration.AGENT_ID);
        if (agentId == null) {
            throw new IllegalStateException("Tool called without an authenticated agent");
        }
        return agentId.toString();
    }
}
