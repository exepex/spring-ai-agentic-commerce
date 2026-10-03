package io.github.exepex.commerce.mcp.downstream.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An order's shipment as the shipping service answers with it. */
public record Shipment(UUID id, UUID orderId, String trackingNumber, String status, LocalDate estimatedDelivery,
        Instant createdAt, Instant shippedAt, Instant deliveredAt, String deliveryProblem, Instant cancelledAt) {}
