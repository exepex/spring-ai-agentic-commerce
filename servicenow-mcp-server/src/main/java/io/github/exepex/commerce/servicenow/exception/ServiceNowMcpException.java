package io.github.exepex.commerce.servicenow.exception;

/**
 * Something the ServiceNow MCP server was asked to do cannot be done. The server has no REST API of its own: thrown
 * in a tool, the message goes back to the agent as the tool's error; thrown while polling, the step is tried again
 * next poll.
 */
public abstract class ServiceNowMcpException extends RuntimeException {

    protected ServiceNowMcpException(String message) {
        super(message);
    }
}
