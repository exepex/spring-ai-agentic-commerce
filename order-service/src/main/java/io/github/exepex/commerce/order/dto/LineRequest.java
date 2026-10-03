package io.github.exepex.commerce.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** One line of an order to place: a product and how many of it. */
public record LineRequest(@NotNull UUID productId, @Positive int quantity) {}
