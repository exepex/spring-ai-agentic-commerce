package io.github.exepex.commerce.agents.exception;

import io.github.exepex.commerce.agents.constants.ErrorMessages;

/** The definition files could not be read from the classpath. */
public class UnreadableAgentDefinitionsException extends AgentDefinitionException {

    public UnreadableAgentDefinitionsException(Throwable cause) {
        super(ErrorMessages.DEFINITIONS_UNREADABLE, cause);
    }
}
