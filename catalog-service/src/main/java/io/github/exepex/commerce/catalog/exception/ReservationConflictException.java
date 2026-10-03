package io.github.exepex.commerce.catalog.exception;

import io.github.exepex.commerce.catalog.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The order already holds a reservation of the product that is no longer held, or is for another quantity. */
public class ReservationConflictException extends CatalogException {

    public ReservationConflictException(UUID orderId, String sku, String existingStatus, int existingQuantity) {
        super(HttpStatus.CONFLICT,
                ErrorMessages.RESERVATION_CONFLICT.formatted(orderId, existingStatus, existingQuantity, sku));
    }
}
