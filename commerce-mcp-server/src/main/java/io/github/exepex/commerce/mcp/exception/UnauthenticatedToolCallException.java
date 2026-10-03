package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;

/** A tool call reached a tool without the agent the request was authenticated as. */
public class UnauthenticatedToolCallException extends RuntimeException {

    public UnauthenticatedToolCallException() {
        super(ErrorMessages.UNAUTHENTICATED_TOOL_CALL);
    }
}
