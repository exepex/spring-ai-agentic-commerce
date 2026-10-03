package io.github.exepex.commerce.order.dto;

import io.github.exepex.commerce.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order with its lines, as the order API shows it. */
public record OrderView(UUID id, String customerEmail, OrderStatus status, BigDecimal total, String currency,
        Instant createdAt, Instant cancelledAt, String cancellationReason, String paymentFailure,
        List<LineView> lines) {}
