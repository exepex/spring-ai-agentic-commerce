package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** No order has this id. */
public class OrderNotFoundException extends OrderException {

    public OrderNotFoundException(UUID orderId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.ORDER_NOT_FOUND.formatted(orderId));
    }
}
