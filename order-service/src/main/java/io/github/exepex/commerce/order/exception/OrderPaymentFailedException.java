package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.constants.OrderValues;
import io.github.exepex.commerce.platform.error.CommerceException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * The card was declined. The order whose payment failed is kept, so the answer names it: callers can link what they
 * record to it.
 */
public class OrderPaymentFailedException extends CommerceException {

    public OrderPaymentFailedException(UUID orderId, String reason) {
        super(HttpStatus.PAYMENT_REQUIRED, reason, Map.of(OrderValues.ORDER_ID_PROPERTY, orderId));
    }
}
