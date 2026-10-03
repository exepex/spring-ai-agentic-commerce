package io.github.exepex.commerce.shipping.exception;

import io.github.exepex.commerce.shipping.Shipment;
import io.github.exepex.commerce.shipping.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The parcel has not shipped, or the carrier already reported another outcome for it. */
public class ShipmentNotOnItsWayException extends ShippingException {

    public ShipmentNotOnItsWayException(UUID orderId, Shipment.Status status) {
        super(HttpStatus.CONFLICT, ErrorMessages.SHIPMENT_NOT_ON_ITS_WAY.formatted(orderId, status));
    }
}
