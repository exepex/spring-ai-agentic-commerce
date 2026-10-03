package io.github.exepex.commerce.mcp.tools.dto;

import io.github.exepex.commerce.mcp.downstream.dto.OrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything about one order: its lines, payment, shipment, refunds and notifications. */
public record OrderDetails(UUID orderId, String customerEmail, String status, BigDecimal total, String currency,
        Instant createdAt, String cancellationReason, List<OrderLine> lines, PaymentSummary payment,
        ShipmentSummary shipment, List<RefundSummary> refunds, List<NotificationSummary> notifications) {}
