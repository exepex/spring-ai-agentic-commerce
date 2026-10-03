package io.github.exepex.commerce.platform.events;

import java.util.Map;

/**
 * Carries the trace an event was raised in across the outbox, so the consumer's work joins the request that caused it,
 * as it did when events were sent straight away.
 */
interface TracePropagation {

    /** The headers that identify the current trace; empty outside one. */
    Map<String, String> currentTrace();

    /** Runs the send inside the trace the headers name, or in a new one when they name none. */
    void continueTrace(Map<String, String> headers, String name, Runnable send);

    TracePropagation NONE = new TracePropagation() {

        @Override
        public Map<String, String> currentTrace() {
            return Map.of();
        }

        @Override
        public void continueTrace(Map<String, String> headers, String name, Runnable send) {
            send.run();
        }
    };
}
