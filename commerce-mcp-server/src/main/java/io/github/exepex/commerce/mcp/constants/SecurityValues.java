package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How an agent proves who it is, and where its id travels from the request to each tool call. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecurityValues {

    public static final String BEARER_PREFIX = "Bearer ";
    /** The request attribute that holds the authenticated agent's id. */
    public static final String AGENT_ID_ATTRIBUTE = "commerce.agentId";
    /** The key of the authenticated agent's id in a tool call's transport context. */
    public static final String AGENT_ID_CONTEXT_KEY = "agentId";
}
