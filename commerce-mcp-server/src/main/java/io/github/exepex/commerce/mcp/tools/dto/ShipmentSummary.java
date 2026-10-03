package io.github.exepex.commerce.mcp.tools.dto;

import java.time.Instant;
import java.time.LocalDate;

/** An order's shipment, and what the carrier reported. */
public record ShipmentSummary(String trackingNumber, String status, LocalDate estimatedDelivery, Instant shippedAt,
        Instant deliveredAt, String deliveryProblem) {}
