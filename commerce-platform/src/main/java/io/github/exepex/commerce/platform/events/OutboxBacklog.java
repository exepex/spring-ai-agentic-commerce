package io.github.exepex.commerce.platform.events;

/** The events waiting in the outbox, and how long the oldest one has waited, in seconds. */
record OutboxBacklog(long waiting, double oldestAgeSeconds) {}
