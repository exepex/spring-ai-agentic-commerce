package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** What the model passed as an order id is not one. */
public class InvalidOrderIdException extends CommerceException {

    public InvalidOrderIdException(String orderId) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.INVALID_ORDER_ID.formatted(orderId));
    }
}
