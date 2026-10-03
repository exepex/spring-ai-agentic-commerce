package io.github.exepex.commerce.order.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The names and codes orders are placed and reported with: payment methods, dependencies and problem properties. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OrderValues {

    /** The Stripe test Visa card, used when an order names no payment method. */
    public static final String DEFAULT_PAYMENT_METHOD = "pm_card_visa";

    /** The catalog as it is named when it cannot be reached. */
    public static final String CATALOG = "catalog";

    /** The problem property that names the order whose payment failed. */
    public static final String ORDER_ID_PROPERTY = "orderId";
}
