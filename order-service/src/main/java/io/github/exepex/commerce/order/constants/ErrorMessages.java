package io.github.exepex.commerce.order.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the order service tells its callers when it cannot do what they asked. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String ORDER_NOT_FOUND = "Order %s does not exist";
    public static final String PRODUCT_NOT_FOUND = "Product %s does not exist";
    public static final String DUPLICATE_PRODUCT = "Product %s appears on more than one line; combine them into one";
    public static final String MIXED_CURRENCIES = "All products in an order must share a currency";
    public static final String ORDER_ID_TAKEN = "Order %s already exists for another customer";
    public static final String ORDER_NOT_CANCELLABLE = "Order %s is %s and cannot be cancelled";
    public static final String ORDER_NOT_SHIPPABLE = "Order %s is %s and cannot ship";
    public static final String DEPENDENCY_UNAVAILABLE = "The %s is unavailable; try again";
    public static final String PAYMENT_SERVICE_UNAVAILABLE = "The payment service is unavailable";
    public static final String CARD_DECLINED = "The card was declined";
}
