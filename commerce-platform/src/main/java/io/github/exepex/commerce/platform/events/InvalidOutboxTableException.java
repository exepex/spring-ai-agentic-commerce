package io.github.exepex.commerce.platform.events;

/** A service is configured with an outbox the relay cannot use, so it refuses to start rather than lose events. */
public class InvalidOutboxTableException extends RuntimeException {

    InvalidOutboxTableException(String table) {
        super("The outbox table must be a plain schema.table name, not '%s'".formatted(table));
    }

    InvalidOutboxTableException(String table, int batchSize) {
        super("The outbox %s needs a batch size of at least 1, not %d".formatted(table, batchSize));
    }
}
