package io.github.exepex.commerce.agents.exception;

import io.github.exepex.commerce.agents.constants.ErrorMessages;

/** Two definition files claim the same agent id, so which one applies would be a guess. */
public class DuplicateAgentDefinitionException extends AgentDefinitionException {

    public DuplicateAgentDefinitionException(String agentId) {
        super(ErrorMessages.DEFINED_TWICE.formatted(agentId));
    }
}
