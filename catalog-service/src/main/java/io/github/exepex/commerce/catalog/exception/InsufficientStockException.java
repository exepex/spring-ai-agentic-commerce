package io.github.exepex.commerce.catalog.exception;

import io.github.exepex.commerce.catalog.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The order asks for more units than are on hand and not yet promised to other orders. */
public class InsufficientStockException extends CommerceException {

    public InsufficientStockException(String sku, int requested, int available) {
        super(HttpStatus.CONFLICT, ErrorMessages.INSUFFICIENT_STOCK.formatted(requested, sku, available));
    }
}
