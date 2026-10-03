package io.github.exepex.commerce.catalog.exception;

import io.github.exepex.commerce.catalog.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The order holds no stock, so there is nothing to ship. */
public class NothingReservedException extends CatalogException {

    public NothingReservedException(UUID orderId) {
        super(HttpStatus.CONFLICT, ErrorMessages.NOTHING_RESERVED.formatted(orderId));
    }
}
