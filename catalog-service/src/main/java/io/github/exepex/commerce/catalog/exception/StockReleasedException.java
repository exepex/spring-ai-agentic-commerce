package io.github.exepex.commerce.catalog.exception;

import io.github.exepex.commerce.catalog.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The order's stock was given back when it was cancelled, so the order cannot ship. */
public class StockReleasedException extends CatalogException {

    public StockReleasedException(UUID orderId) {
        super(HttpStatus.CONFLICT, ErrorMessages.STOCK_RELEASED.formatted(orderId));
    }
}
