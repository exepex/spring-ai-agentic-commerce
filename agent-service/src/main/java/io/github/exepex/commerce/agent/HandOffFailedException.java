package io.github.exepex.commerce.agent;

/** Neither the agent nor a team got the incident: Kafka keeps delivering it until one does. */
final class HandOffFailedException extends IllegalStateException {

    HandOffFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
