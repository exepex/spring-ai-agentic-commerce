package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The systems the audit trail and cases name as the actor, for what they did or announced. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Actors {

    /** What ServiceNow did, as reported by its poller. */
    public static final String SERVICENOW = "servicenow";
    public static final String CATALOG_SERVICE = "catalog-service";
    public static final String ORDER_SERVICE = "order-service";
    public static final String PAYMENT_SERVICE = "payment-service";
    public static final String SHIPPING_SERVICE = "shipping-service";
}
