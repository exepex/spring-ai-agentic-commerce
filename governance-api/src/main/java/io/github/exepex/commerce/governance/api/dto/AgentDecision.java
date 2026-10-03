package io.github.exepex.commerce.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** Why an agent did what it did, with the model and the tokens behind it, for the audit trail. */
public record AgentDecision(UUID orderId, @NotBlank String summary, String reasoning, String model, Long inputTokens,
        Long outputTokens, Long durationMillis) {}
