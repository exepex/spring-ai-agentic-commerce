package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The agent API's addresses, and those of the commerce MCP server's API that agent-service calls. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    public static final String ASSISTANT_CHAT = "/api/assistant/chat";
    public static final String AGENTS = "/api/agents";
    public static final String AGENT_ID = "/{agentId}";
    public static final String AGENT = AGENTS + AGENT_ID;

    /** The commerce MCP server's kill switches. */
    public static final String AGENT_SWITCHES = "/api/agent-switches";
    /** The commerce MCP server's API where agents record their decisions. */
    public static final String GOVERNANCE_AGENT_API = "/api/agent";
    public static final String DECISIONS = "/decisions";
}
