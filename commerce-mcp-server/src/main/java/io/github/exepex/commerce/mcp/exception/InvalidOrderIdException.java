package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** What the model passed as an order id is not one. */
public class InvalidOrderIdException extends GovernanceException {

    public InvalidOrderIdException(String orderId) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.INVALID_ORDER_ID.formatted(orderId));
    }
}
