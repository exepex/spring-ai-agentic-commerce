package io.github.exepex.commerce.order.dto;

import java.util.UUID;

/** A product and quantity a customer asks for at checkout. */
public record RequestedLine(UUID productId, int quantity) {}
