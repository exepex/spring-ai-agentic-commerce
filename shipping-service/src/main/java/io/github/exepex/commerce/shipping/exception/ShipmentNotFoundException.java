package io.github.exepex.commerce.shipping.exception;

import io.github.exepex.commerce.shipping.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The order has no parcel: it was never confirmed, or the confirmation has not arrived yet. */
public class ShipmentNotFoundException extends ShippingException {

    public ShipmentNotFoundException(UUID orderId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.SHIPMENT_NOT_FOUND.formatted(orderId));
    }
}
