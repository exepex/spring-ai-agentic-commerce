package io.github.exepex.commerce.catalog.dto;

import java.time.Instant;
import java.util.UUID;

/** Units of one product held for one order, as the catalog API shows them. */
public record ReservationView(UUID id, UUID orderId, UUID productId, int quantity, String status, Instant createdAt) {}
