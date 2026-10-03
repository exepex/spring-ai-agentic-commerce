package io.github.exepex.commerce.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One line of an order, as the order API shows it. */
public record LineView(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice,
        BigDecimal lineTotal) {}
