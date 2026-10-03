package io.github.exepex.commerce.mcp.downstream.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A product as the catalog service answers with it. */
public record Product(UUID id, String sku, String name, String description, BigDecimal price, String currency,
        int available) {}
