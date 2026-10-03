package io.github.exepex.commerce.mcp.governance.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** Why an agent did what it did, with the model and token usage behind it. */
public record AgentDecision(UUID orderId, @NotBlank String summary, String reasoning, String model, Long inputTokens,
        Long outputTokens, Long durationMillis) {}
