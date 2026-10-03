package io.github.exepex.commerce.shipping.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the shipping service tells its callers when it cannot do what they asked. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String SHIPMENT_NOT_FOUND = "Order %s has no shipment";
    public static final String NOT_A_CARRIER_OUTCOME = "The carrier reports DELIVERED, DELIVERY_FAILED or LOST, not %s";
    public static final String SHIPMENT_NOT_ON_ITS_WAY = "The shipment of order %s is %s; the carrier only reports on a "
            + "parcel that has shipped and is still on its way";
    public static final String NO_CARRIER_REPORT = "The carrier did not report on shipment %s";
}
