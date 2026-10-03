package io.github.exepex.commerce.mcp.downstream.dto;

import java.util.List;
import java.util.UUID;

/** Placing is idempotent by {@code orderId}: asking again returns the order placed the first time. */
public record PlaceOrderRequest(UUID orderId, String customerEmail, List<RequestedLine> lines, String paymentMethod) {}
