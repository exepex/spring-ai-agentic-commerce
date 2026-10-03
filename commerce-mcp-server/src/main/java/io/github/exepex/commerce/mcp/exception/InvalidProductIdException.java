package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** What the model passed as a product id is not one. */
public class InvalidProductIdException extends CommerceException {

    public InvalidProductIdException(String productId) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.INVALID_PRODUCT_ID.formatted(productId));
    }
}
