package io.github.exepex.commerce.mcpserver.exception;

import io.github.exepex.commerce.mcpserver.constants.McpServerMessages;

/** A tool was called without an agent the filter let in; the transport is not wired as it should be. */
public class UnauthenticatedToolCallException extends RuntimeException {

    public UnauthenticatedToolCallException() {
        super(McpServerMessages.UNAUTHENTICATED_TOOL_CALL);
    }
}
