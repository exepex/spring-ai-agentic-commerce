package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The agent API's addresses. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    public static final String ASSISTANT_CHAT = "/api/assistant/chat";
    public static final String AGENTS = "/api/agents";
    public static final String AGENT_ID = "/{agentId}";
    public static final String AGENT = AGENTS + AGENT_ID;
}
