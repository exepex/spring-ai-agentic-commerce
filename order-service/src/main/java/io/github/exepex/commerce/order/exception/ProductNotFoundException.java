package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The catalog has no product with this id, so it cannot be ordered. */
public class ProductNotFoundException extends OrderException {

    public ProductNotFoundException(UUID productId) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.PRODUCT_NOT_FOUND.formatted(productId));
    }
}
