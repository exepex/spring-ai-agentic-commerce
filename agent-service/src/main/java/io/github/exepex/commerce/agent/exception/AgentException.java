package io.github.exepex.commerce.agent.exception;

/**
 * Something an agent run or its setup cannot do. None of these reach a caller of the agent API: the chat answers with
 * a reply the customer can read, and an incident's failure goes back to Kafka.
 */
public abstract class AgentException extends RuntimeException {

    protected AgentException(String message) {
        this(message, null);
    }

    protected AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
