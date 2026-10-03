package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.ErrorMessages;

/** A tool call reached a tool without the agent the bearer token named. */
public class UnauthenticatedToolCallException extends ServiceNowMcpException {

    public UnauthenticatedToolCallException() {
        super(ErrorMessages.UNAUTHENTICATED_TOOL_CALL);
    }
}
