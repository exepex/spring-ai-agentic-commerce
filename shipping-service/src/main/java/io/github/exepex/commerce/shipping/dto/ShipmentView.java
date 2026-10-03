package io.github.exepex.commerce.shipping.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An order's parcel and where it is, as the shipping API shows it. */
public record ShipmentView(UUID id, UUID orderId, String customerEmail, String trackingNumber, String status,
        LocalDate estimatedDelivery, Instant createdAt, Instant shippedAt, Instant deliveredAt, String deliveryProblem,
        Instant cancelledAt) {}
