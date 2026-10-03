package io.github.exepex.commerce.catalog.exception;

import io.github.exepex.commerce.catalog.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** No product has this id. */
public class ProductNotFoundException extends CommerceException {

    public ProductNotFoundException(UUID productId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.PRODUCT_NOT_FOUND.formatted(productId));
    }
}
