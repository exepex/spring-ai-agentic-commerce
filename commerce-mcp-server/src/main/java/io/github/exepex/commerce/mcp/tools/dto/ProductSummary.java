package io.github.exepex.commerce.mcp.tools.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A product as the shop's tools show it, with how many are available. */
public record ProductSummary(UUID productId, String sku, String name, String description, BigDecimal price,
        String currency, int available) {}
