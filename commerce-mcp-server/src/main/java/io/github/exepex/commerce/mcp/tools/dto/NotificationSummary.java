package io.github.exepex.commerce.mcp.tools.dto;

import java.time.Instant;

/** That the customer was told something about the order, and when: what an agent needs to avoid telling them twice. */
public record NotificationSummary(Instant sentAt, String sentBy) {}
