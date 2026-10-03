package io.github.exepex.commerce.agent.exception;

import io.github.exepex.commerce.agent.constants.ErrorMessages;

/** An agent tool was called without the run it belongs to, so its rules could not be applied. */
public class ToolCalledOutsideRunException extends AgentException {

    public ToolCalledOutsideRunException() {
        super(ErrorMessages.TOOL_CALLED_OUTSIDE_RUN);
    }
}
