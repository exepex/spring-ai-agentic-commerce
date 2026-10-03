package io.github.exepex.commerce.shipping.exception;

import io.github.exepex.commerce.platform.error.CommerceException;
import io.github.exepex.commerce.shipping.Shipment;
import io.github.exepex.commerce.shipping.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** The report names a status the carrier cannot give a parcel, such as PREPARING. */
public class NotACarrierOutcomeException extends CommerceException {

    public NotACarrierOutcomeException(Shipment.Status outcome) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.NOT_A_CARRIER_OUTCOME.formatted(outcome));
    }
}
