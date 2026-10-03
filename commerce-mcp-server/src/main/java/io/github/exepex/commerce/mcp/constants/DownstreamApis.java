package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The commerce services the MCP server calls: how they are named to agents and people, their addresses and values. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DownstreamApis {

    public static final String CATALOG = "catalog";
    public static final String ORDER_SERVICE = "order service";
    public static final String PAYMENT_SERVICE = "payment service";
    public static final String SHIPPING_SERVICE = "shipping service";

    public static final String CATALOG_API = "/api";
    public static final String PRODUCTS = "/products";
    public static final String ORDERS = "/api/orders";
    public static final String PAYMENTS = "/api/payments";
    public static final String SHIPMENTS = "/api/shipments";
    public static final String BY_ORDER_ID = "/{orderId}";
    public static final String ORDER_CANCELLATION = BY_ORDER_ID + "/cancellation";
    public static final String REFUNDS = BY_ORDER_ID + "/refunds";

    /** An order in these is still being placed or paid; in any other it was placed and paid first. */
    public static final String ORDER_PLACED = "PLACED";
    public static final String ORDER_PAYMENT_PENDING = "PAYMENT_PENDING";

    /** The card a confirmation is paid with when the customer names none. */
    public static final String DEFAULT_PAYMENT_METHOD = "pm_card_visa";
    /** The payment status shown when the payment service could not be read. */
    public static final String PAYMENT_STATUS_UNKNOWN = "UNKNOWN";
    /** The problem-detail property in which the order service names an order whose payment failed. */
    public static final String ORDER_ID_PROPERTY = "orderId";
}
