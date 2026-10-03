package io.github.exepex.commerce.agents.exception;

import io.github.exepex.commerce.agents.constants.ErrorMessages;

/** No definition file defines the agent asked for. */
public class MissingAgentDefinitionException extends AgentDefinitionException {

    public MissingAgentDefinitionException(String agentId) {
        super(ErrorMessages.NO_SUCH_AGENT.formatted(agentId));
    }
}
