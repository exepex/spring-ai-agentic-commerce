package io.github.exepex.commerce.servicenow.governance.dto;

import java.util.UUID;

/** One tool call, recorded as the agent that made it; {@code outcome} is SUCCEEDED, FAILED or DENIED. */
public record ToolCall(UUID orderId, String action, String outcome, String summary, String details) {}
