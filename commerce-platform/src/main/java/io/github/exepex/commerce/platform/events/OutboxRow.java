package io.github.exepex.commerce.platform.events;

/** One event waiting in the outbox: its topic, key and JSON as the service's Kafka serializer wrote it. */
record OutboxRow(long id, String topic, String key, String payload, String traceHeaders) {}
