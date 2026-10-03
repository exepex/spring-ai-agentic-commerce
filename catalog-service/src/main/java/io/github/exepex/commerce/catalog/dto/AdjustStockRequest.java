package io.github.exepex.commerce.catalog.dto;

import jakarta.validation.constraints.NotBlank;

/** A change to the units on hand: a delivery (positive) or a write-off (negative), and why. */
public record AdjustStockRequest(int delta, @NotBlank String reason) {}
