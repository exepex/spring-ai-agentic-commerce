package io.github.exepex.commerce.mcpserver.exception;

import io.github.exepex.commerce.mcpserver.constants.McpServerMessages;

/** An agent may use this server's tools but has no token, so it could never call them: the server does not start. */
public class MissingAgentTokenException extends RuntimeException {

    public MissingAgentTokenException(String agentId) {
        super(McpServerMessages.MISSING_AGENT_TOKEN.formatted(agentId));
    }
}
