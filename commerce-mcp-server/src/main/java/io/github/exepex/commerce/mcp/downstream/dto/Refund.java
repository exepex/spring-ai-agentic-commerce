package io.github.exepex.commerce.mcp.downstream.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A refund as the payment service answers with it. */
public record Refund(UUID id, BigDecimal amount, String reason, String idempotencyKey, String providerReference,
        Instant createdAt) {}
