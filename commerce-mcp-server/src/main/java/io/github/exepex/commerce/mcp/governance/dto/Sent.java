package io.github.exepex.commerce.mcp.governance.dto;

import io.github.exepex.commerce.mcp.governance.CustomerNotification;

/** A notification, and whether this call sent it or an earlier one with the same key did. */
public record Sent(CustomerNotification notification, boolean now) {}
