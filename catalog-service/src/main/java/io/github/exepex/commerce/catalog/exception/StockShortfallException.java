package io.github.exepex.commerce.catalog.exception;

import io.github.exepex.commerce.catalog.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** A stock-out left the order's reservation uncovered: its units are not in the warehouse, so it cannot ship. */
public class StockShortfallException extends CommerceException {

    public StockShortfallException(UUID orderId, String sku) {
        super(HttpStatus.CONFLICT, ErrorMessages.STOCK_SHORTFALL.formatted(sku, orderId));
    }
}
