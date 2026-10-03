package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The product is on more than one line; each product goes on one line, so it is reserved and charged once. */
public class DuplicateProductException extends OrderException {

    public DuplicateProductException(UUID productId) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.DUPLICATE_PRODUCT.formatted(productId));
    }
}
