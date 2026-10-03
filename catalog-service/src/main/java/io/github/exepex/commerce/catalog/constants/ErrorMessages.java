package io.github.exepex.commerce.catalog.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the catalog service tells its callers when it cannot do what they asked. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String PRODUCT_NOT_FOUND = "Product %s does not exist";
    public static final String INSUFFICIENT_STOCK = "Requested %s of %s but only %s available";
    public static final String RESERVATION_CONFLICT = "Order %s already has a %s reservation of %s %s";
    public static final String INVALID_STOCK_ADJUSTMENT = "Adjusting %s by %s would take its %s units on hand below zero";
    public static final String NOTHING_RESERVED = "Order %s has no stock reserved";
    public static final String STOCK_RELEASED = "The stock of order %s was released; the order cannot ship";
    public static final String STOCK_SHORTFALL =
            "The stock on hand of %s no longer covers order %s after a stock-out; the order cannot ship";
}
