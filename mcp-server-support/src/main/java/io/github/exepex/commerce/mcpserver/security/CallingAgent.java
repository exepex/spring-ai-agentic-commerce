package io.github.exepex.commerce.mcpserver.security;

import io.github.exepex.commerce.mcpserver.exception.UnauthenticatedToolCallException;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Who is calling: the agent the {@link AgentAuthenticationFilter} let in, as a request attribute for an API an agent
 * reports to, and in each tool call's transport context.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CallingAgent {

    /** The request attribute that holds the authenticated agent's id, for {@code @RequestAttribute}. */
    public static final String REQUEST_ATTRIBUTE = "commerce.agentId";
    static final String CONTEXT_KEY = "agentId";

    public static String of(McpTransportContext context) {
        var agentId = context.get(CONTEXT_KEY);
        if (agentId == null) {
            throw new UnauthenticatedToolCallException();
        }
        return agentId.toString();
    }
}
