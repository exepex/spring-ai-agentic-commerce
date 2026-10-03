package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** A proposed line names a product the catalog does not have. */
public class ProductNotFoundException extends GovernanceException {

    public ProductNotFoundException(String productId) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.PRODUCT_NOT_FOUND.formatted(productId));
    }
}
