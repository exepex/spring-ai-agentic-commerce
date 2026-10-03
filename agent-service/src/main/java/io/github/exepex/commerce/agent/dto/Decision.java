package io.github.exepex.commerce.agent.dto;

import java.util.UUID;

/** An agent's decision, with the model and the tokens it used, sent to the commerce MCP server's audit trail. */
public record Decision(UUID orderId, String summary, String reasoning, String model, Long inputTokens,
        Long outputTokens, Long durationMillis) {}
