package io.github.exepex.commerce.order.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The settings in {@code application.yml} that the code reads by name. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigKeys {

    /** The HTTP service groups; each one's base URL is {@code spring.http.serviceclient.<group>.base-url}. */
    public static final String CATALOG_CLIENT_GROUP = "catalog";
    public static final String PAYMENT_CLIENT_GROUP = "payment";

    public static final String ORDER_EVENTS_TOPIC = "${commerce.topics.order-events}";
    public static final String SHIPMENT_EVENTS_TOPIC = "${commerce.topics.shipment-events}";
    public static final String RECONCILIATION_INTERVAL = "${commerce.reconciliation.interval}";
    public static final String RECONCILIATION_SETTLE_AFTER = "${commerce.reconciliation.settle-after}";
}
