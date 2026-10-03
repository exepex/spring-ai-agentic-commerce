package io.github.exepex.commerce.platform.events;

import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where a service keeps the events it has not yet put on Kafka, and how the relay moves them.
 *
 * @param table        the outbox table, qualified with the service's schema
 * @param batchSize    how many events the relay sends per round
 * @param pollInterval how long the relay waits for new events when nothing woke it
 * @param sendTimeout  how long the relay waits for Kafka to take an event before trying again later
 */
@ConfigurationProperties("commerce.events.outbox")
public record OutboxProperties(
        @DefaultValue("public.outbox_event") String table,
        @DefaultValue("500") int batchSize,
        @DefaultValue("1s") Duration pollInterval,
        @DefaultValue("10s") Duration sendTimeout) {

    /** Only a plain schema.table goes into the outbox's statements. */
    private static final Pattern QUALIFIED_TABLE = Pattern.compile("[a-z_][a-z0-9_]*\\.[a-z_][a-z0-9_]*");

    public OutboxProperties {
        if (!QUALIFIED_TABLE.matcher(table).matches()) {
            throw new InvalidOutboxTableException(table);
        }
        if (batchSize < 1) {
            throw new InvalidOutboxTableException(table, batchSize);
        }
    }
}
