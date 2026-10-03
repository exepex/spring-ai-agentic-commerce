package io.github.exepex.commerce.mcp.governance.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A line of a proposed order, at the catalog's price when it was proposed. */
public record ProposedLine(UUID productId, String sku, String name, int quantity, BigDecimal unitPrice) {}
