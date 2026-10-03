package io.github.exepex.commerce.mcp.tools.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A refund already requested for an order, with the key that retries it. */
public record RefundSummary(UUID refundRequestId, BigDecimal amount, String status, String reason,
        String idempotencyKey) {}
