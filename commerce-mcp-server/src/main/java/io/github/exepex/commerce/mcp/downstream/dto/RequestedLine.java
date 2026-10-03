package io.github.exepex.commerce.mcp.downstream.dto;

import java.util.UUID;

/** A product and quantity to place an order for. */
public record RequestedLine(UUID productId, int quantity) {}
