package io.github.exepex.commerce.mcp.downstream.dto;

import java.math.BigDecimal;

/** A refund asked of the payment service; the idempotency key makes a retry pay out at most once. */
public record RefundRequest(BigDecimal amount, String reason, String idempotencyKey) {}
