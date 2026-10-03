package io.github.exepex.commerce.mcp.governance.dto;

/** A product and quantity the model asks to put on a proposed order. */
public record RequestedLine(String productId, int quantity) {}
