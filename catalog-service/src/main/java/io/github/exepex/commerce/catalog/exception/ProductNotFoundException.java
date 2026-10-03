package io.github.exepex.commerce.catalog.exception;

import io.github.exepex.commerce.catalog.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** No product has this id. */
public class ProductNotFoundException extends CatalogException {

    public ProductNotFoundException(UUID productId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.PRODUCT_NOT_FOUND.formatted(productId));
    }
}
