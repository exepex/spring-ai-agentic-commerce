package io.github.exepex.commerce.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published when stock on hand can no longer cover the units promised to open orders.
 *
 * <p>{@code affectedOrderIds} names the most recent reservations that together cover the shortfall: the orders
 * that cannot be fulfilled as placed. What happens to them is decided downstream.
 */
public record StockOutEvent(
        UUID eventId,
        Instant occurredAt,
        UUID productId,
        String sku,
        int onHand,
        int reserved,
        int shortfall,
        String reason,
        List<UUID> affectedOrderIds) {}
