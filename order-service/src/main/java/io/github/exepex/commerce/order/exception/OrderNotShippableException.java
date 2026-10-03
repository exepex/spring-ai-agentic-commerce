package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.OrderStatus;
import io.github.exepex.commerce.order.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The order's status does not allow it to ship. */
public class OrderNotShippableException extends CommerceException {

    public OrderNotShippableException(UUID orderId, OrderStatus status) {
        super(HttpStatus.CONFLICT, ErrorMessages.ORDER_NOT_SHIPPABLE.formatted(orderId, status));
    }
}
