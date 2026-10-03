package io.github.exepex.commerce.order.dto;

import java.util.UUID;

/** Asks the catalog to reserve a product's stock for an order. */
public record ReserveStockRequest(UUID orderId, int quantity) {}
