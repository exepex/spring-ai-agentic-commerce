package io.github.exepex.commerce.agent.exception;

import io.github.exepex.commerce.agent.constants.ErrorMessages;

/**
 * Credentials were asked for an agent that agent-service does not run. It never reaches a caller of the agent API:
 * the chat answers with a reply the customer can read.
 */
public class UnknownAgentException extends AgentException {

    public UnknownAgentException(String agentId) {
        super(ErrorMessages.UNKNOWN_AGENT.formatted(agentId));
    }
}
