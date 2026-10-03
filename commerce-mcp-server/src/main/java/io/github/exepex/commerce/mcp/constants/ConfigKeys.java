package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The settings in {@code application.yml} that the code reads by name. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigKeys {

    public static final String GOVERNANCE_PREFIX = "commerce.governance";
    public static final String CASE_WORKER = "${commerce.cases.worker}";
    public static final String RECONCILIATION_INTERVAL = "${commerce.reconciliation.interval}";
    public static final String RECONCILIATION_SETTLE_AFTER = "${commerce.reconciliation.settle-after}";

    public static final String ORDER_EVENTS_TOPIC = "${commerce.topics.order-events}";
    public static final String PAYMENT_EVENTS_TOPIC = "${commerce.topics.payment-events}";
    public static final String SHIPMENT_EVENTS_TOPIC = "${commerce.topics.shipment-events}";
    public static final String STOCK_OUT_TOPIC = "${commerce.topics.stock-out}";

    /** The HTTP service groups, each configured under {@code spring.http.serviceclient.<group>}. */
    public static final String CATALOG_CLIENT = "catalog";
    public static final String ORDER_CLIENT = "order";
    public static final String PAYMENT_CLIENT = "payment";
    public static final String SHIPPING_CLIENT = "shipping";
}
