package io.github.exepex.commerce.mcpserver.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What an MCP server tells an agent, or its operator, when an agent cannot be let in. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class McpServerMessages {

    public static final String AGENT_TOKEN_REQUIRED = "An agent bearer token is required";
    public static final String MISSING_AGENT_TOKEN = "No token is configured for agent %s";
    public static final String UNAUTHENTICATED_TOOL_CALL = "Tool called without an authenticated agent";
}
