package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.RefusalMessages;

/**
 * A tool call the rules refuse; its message goes back to the agent and says what to do instead. It starts with
 * {@value RefusalMessages#PREFIX}, so a caller can tell a refusal, which asking again will not change, from a failure
 * such as ServiceNow being down.
 */
public abstract class ToolRefusedException extends RuntimeException {

    protected ToolRefusedException(String message) {
        super(RefusalMessages.PREFIX + message);
    }
}
