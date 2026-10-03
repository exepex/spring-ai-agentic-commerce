package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.OrderStatus;
import io.github.exepex.commerce.order.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The order's status no longer, or not yet, allows a cancellation. */
public class OrderNotCancellableException extends OrderException {

    public OrderNotCancellableException(UUID orderId, OrderStatus status) {
        super(HttpStatus.CONFLICT, ErrorMessages.ORDER_NOT_CANCELLABLE.formatted(orderId, status));
    }
}
