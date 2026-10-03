package io.github.exepex.commerce.agents.exception;

import io.github.exepex.commerce.agents.constants.ErrorMessages;

/** A definition file is malformed or lacks a setting, so the agent it defines cannot run. */
public class InvalidAgentDefinitionException extends AgentDefinitionException {

    /** @param problem what is wrong with the file, from {@link ErrorMessages} */
    public InvalidAgentDefinitionException(String fileName, String problem) {
        super(ErrorMessages.INVALID_DEFINITION.formatted(fileName, problem));
    }
}
