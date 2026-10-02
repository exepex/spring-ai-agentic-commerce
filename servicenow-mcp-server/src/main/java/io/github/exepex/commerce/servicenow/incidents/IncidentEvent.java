package io.github.exepex.commerce.servicenow.incidents;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on the {@code servicenow.incidents} topic, keyed by incident number, once the poller has claimed a new
 * incident for the incident agent.
 */
public record IncidentEvent(UUID eventId, String number, String shortDescription, Instant occurredAt) {}
