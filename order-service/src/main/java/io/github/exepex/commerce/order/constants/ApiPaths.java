package io.github.exepex.commerce.order.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The order API's addresses, and the catalog and payment endpoints this service calls. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    public static final String ORDERS = "/api/orders";
    public static final String ORDER = "/{orderId}";
    public static final String ORDER_DISPATCH = ORDER + "/dispatch";
    public static final String ORDER_CANCELLATION = ORDER + "/cancellation";
    public static final String ORDER_LOCATION = ORDERS + "/%s";

    public static final String REMOTE_API = "/api";
    public static final String CATALOG_PRODUCT = "/products/{productId}";
    public static final String CATALOG_PRODUCT_RESERVATIONS = CATALOG_PRODUCT + "/reservations";
    public static final String CATALOG_ORDER_RESERVATIONS = "/orders/{orderId}/reservations";
    public static final String CATALOG_ORDER_DISPATCH = "/orders/{orderId}/dispatch";
    public static final String PAYMENTS = "/payments";
}
