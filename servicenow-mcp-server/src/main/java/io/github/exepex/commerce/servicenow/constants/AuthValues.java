package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How an agent's identity is presented and passed on to its tool calls. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthValues {

    public static final String BEARER_PREFIX = "Bearer ";
    /** The request attribute that carries the authenticated agent's id to the MCP transport. */
    public static final String AGENT_ID_ATTRIBUTE = "servicenow.agentId";
    /** The key of the agent's id in a tool call's transport context. */
    public static final String AGENT_ID = "agentId";
}
