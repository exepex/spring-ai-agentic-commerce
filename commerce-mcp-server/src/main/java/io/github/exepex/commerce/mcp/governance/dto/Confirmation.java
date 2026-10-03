package io.github.exepex.commerce.mcp.governance.dto;

/** The customer's confirmation of a proposed order, with the card to pay with. */
public record Confirmation(String paymentMethod) {}
