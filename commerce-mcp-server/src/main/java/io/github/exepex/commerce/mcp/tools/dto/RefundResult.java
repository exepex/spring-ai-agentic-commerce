package io.github.exepex.commerce.mcp.tools.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** The refund's outcome, with what the agent should do next. */
public record RefundResult(UUID refundRequestId, String status, BigDecimal amount, String currency, String message) {}
