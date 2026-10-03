package io.github.exepex.commerce.shipping.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The shipping API's addresses. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    public static final String SHIPMENTS = "/api/shipments";
    public static final String SHIPMENT = SHIPMENTS + "/{orderId}";
    public static final String CARRIER_REPORTS = SHIPMENT + "/carrier-reports";
}
