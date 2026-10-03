package io.github.exepex.commerce.mcp.downstream.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A line of an order as the order service answers with it. */
public record OrderLine(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice,
        BigDecimal lineTotal) {}
