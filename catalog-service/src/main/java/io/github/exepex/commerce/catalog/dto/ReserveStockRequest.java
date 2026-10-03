package io.github.exepex.commerce.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** Units of one product to hold for an order. */
public record ReserveStockRequest(@NotNull UUID orderId, @Positive int quantity) {}
