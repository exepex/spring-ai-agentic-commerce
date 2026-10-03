package io.github.exepex.commerce.agents.exception;

/** The agent definitions cannot be used as they are; the service that reads them refuses to start. */
public abstract class AgentDefinitionException extends RuntimeException {

    protected AgentDefinitionException(String message) {
        this(message, null);
    }

    protected AgentDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
