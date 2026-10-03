package io.github.exepex.commerce.mcp.tools.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An order in a list. {@code items} says what was ordered, such as "2 x Headlamp, 1 x Tent"; null when not shown. */
public record OrderSummary(UUID orderId, String status, BigDecimal total, String currency, Instant createdAt,
        String items) {}
