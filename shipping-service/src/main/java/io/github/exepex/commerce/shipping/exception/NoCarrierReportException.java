package io.github.exepex.commerce.shipping.exception;

import io.github.exepex.commerce.shipping.constants.ErrorMessages;
import java.util.UUID;

/** A shipment event was asked for a parcel the carrier has not reported on; only a carrier's report is announced. */
public class NoCarrierReportException extends RuntimeException {

    public NoCarrierReportException(UUID shipmentId) {
        super(ErrorMessages.NO_CARRIER_REPORT.formatted(shipmentId));
    }
}
