package io.github.exepex.commerce.order.dto;

import jakarta.validation.constraints.NotBlank;

/** Why the order is cancelled. */
public record CancelOrderRequest(@NotBlank String reason) {}
