package io.github.exepex.commerce.mcp.security;

import io.github.exepex.commerce.mcp.constants.SecurityValues;
import io.github.exepex.commerce.mcp.exception.UnauthenticatedToolCallException;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reads the authenticated agent's id from a tool call's transport context. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CallingAgent {

    public static String of(McpTransportContext context) {
        var agentId = context.get(SecurityValues.AGENT_ID_CONTEXT_KEY);
        if (agentId == null) {
            throw new UnauthenticatedToolCallException();
        }
        return agentId.toString();
    }
}
