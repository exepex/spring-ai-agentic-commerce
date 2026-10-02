package io.github.exepex.commerce.servicenow.incidents;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on the {@code servicenow.incidents} topic, keyed by incident number, once the poller has claimed a new
 * incident for the incident agent. {@code orderId} is the order linked in the incident's Correlation ID field, the
 * only order the agent may change while working it; empty when the incident names none.
 */
public record IncidentEvent(UUID eventId, String number, String shortDescription, String orderId, Instant occurredAt) {}
