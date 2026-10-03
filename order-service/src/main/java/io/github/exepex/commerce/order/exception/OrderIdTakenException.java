package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The order id asked for already belongs to another customer's order. */
public class OrderIdTakenException extends CommerceException {

    public OrderIdTakenException(UUID orderId) {
        super(HttpStatus.CONFLICT, ErrorMessages.ORDER_ID_TAKEN.formatted(orderId));
    }
}
