package io.github.exepex.commerce.catalog.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The catalog API's addresses. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    public static final String PRODUCTS = "/api/products";
    public static final String PRODUCT = PRODUCTS + "/{productId}";
    public static final String PRODUCT_RESERVATIONS = PRODUCT + "/reservations";
    public static final String STOCK_ADJUSTMENTS = PRODUCT + "/stock-adjustments";
    public static final String ORDER = "/api/orders/{orderId}";
    public static final String ORDER_RESERVATIONS = ORDER + "/reservations";
    public static final String ORDER_DISPATCH = ORDER + "/dispatch";
}
