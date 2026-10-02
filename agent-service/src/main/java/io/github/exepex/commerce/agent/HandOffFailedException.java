package io.github.exepex.commerce.agent;

/**
 * Neither the agent nor a person got the work: Kafka keeps delivering the event (a stock-out or an incident) until
 * one does.
 */
final class HandOffFailedException extends IllegalStateException {

    HandOffFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
