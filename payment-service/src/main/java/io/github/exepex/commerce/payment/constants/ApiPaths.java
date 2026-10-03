package io.github.exepex.commerce.payment.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The payment API's addresses. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    public static final String PAYMENTS = "/api/payments";
    public static final String PAYMENT = PAYMENTS + "/{orderId}";
    public static final String REFUNDS = PAYMENT + "/refunds";
    public static final String SIMULATED_OUTAGE = "/api/admin/simulated-outage";
}
