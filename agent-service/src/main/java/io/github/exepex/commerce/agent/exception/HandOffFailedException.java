package io.github.exepex.commerce.agent.exception;

/** Neither the agent nor a team got the incident: Kafka keeps delivering it until one does. */
public abstract class HandOffFailedException extends AgentException {

    protected HandOffFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
