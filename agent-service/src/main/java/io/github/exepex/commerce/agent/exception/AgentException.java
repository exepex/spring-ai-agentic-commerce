package io.github.exepex.commerce.agent.exception;

/**
 * Something an agent run or its setup cannot do, such as acting for an agent that does not exist. None of these reach
 * a caller of the agent API: the chat answers with a reply the customer can read.
 */
public abstract class AgentException extends RuntimeException {

    protected AgentException(String message) {
        super(message);
    }
}
