package io.github.exepex.commerce.platform.consumers;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How a service retries an event its listener could not handle before it parks the event on the topic's dead-letter
 * topic ({@code <topic>.DLT}), so one bad event never blocks the ones behind it and none is silently dropped.
 *
 * @param retries        how often an event is tried again
 * @param firstRetry     how long before the first retry; each retry waits twice as long as the one before
 * @param longestBackOff the longest wait between two tries
 */
@ConfigurationProperties("commerce.consumers")
public record ConsumerProperties(
        @DefaultValue("4") int retries,
        @DefaultValue("1s") Duration firstRetry,
        @DefaultValue("10s") Duration longestBackOff) {}
