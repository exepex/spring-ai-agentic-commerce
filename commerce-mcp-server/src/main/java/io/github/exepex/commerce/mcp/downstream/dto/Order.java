package io.github.exepex.commerce.mcp.downstream.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order as the order service answers with it. */
public record Order(UUID id, String customerEmail, String status, BigDecimal total, String currency,
        Instant createdAt, Instant cancelledAt, String cancellationReason, String paymentFailure,
        List<OrderLine> lines) {}
