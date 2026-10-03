package io.github.exepex.commerce.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A product and its stock, as the catalog API shows it. */
public record ProductView(UUID id, String sku, String name, String description, BigDecimal price, String currency,
        int onHand, int reserved, int available) {}
