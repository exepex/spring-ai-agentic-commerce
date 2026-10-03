package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.ErrorMessages;

/** An agent with ServiceNow tools in its definition has no token; the server refuses to start. */
public class MissingAgentTokenException extends RuntimeException {

    public MissingAgentTokenException(String agentId) {
        super(ErrorMessages.MISSING_AGENT_TOKEN.formatted(agentId));
    }
}
