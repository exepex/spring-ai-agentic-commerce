package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** A proposed line asks for fewer than one, or for more than the catalog has available now. */
public class InsufficientStockException extends CommerceException {

    public InsufficientStockException(int quantity, String productName, int available) {
        super(HttpStatus.CONFLICT, ErrorMessages.INSUFFICIENT_STOCK.formatted(quantity, productName, available));
    }
}
