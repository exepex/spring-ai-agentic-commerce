package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;

/** An agent has a definition but no bearer token configured; the server refuses to start without one. */
public class MissingAgentTokenException extends RuntimeException {

    public MissingAgentTokenException(String agentId) {
        super(ErrorMessages.MISSING_AGENT_TOKEN.formatted(agentId));
    }
}
