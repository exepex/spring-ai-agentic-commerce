package io.github.exepex.commerce.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A product as the catalog answers with it. */
public record CatalogProduct(UUID id, String sku, String name, BigDecimal price, String currency, int available) {}
