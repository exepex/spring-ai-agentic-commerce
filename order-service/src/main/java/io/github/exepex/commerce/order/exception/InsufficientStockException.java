package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The catalog could not reserve the stock asked for; the message is the catalog's own explanation. */
public class InsufficientStockException extends CommerceException {

    public InsufficientStockException(String catalogDetail) {
        super(HttpStatus.CONFLICT, catalogDetail);
    }
}
