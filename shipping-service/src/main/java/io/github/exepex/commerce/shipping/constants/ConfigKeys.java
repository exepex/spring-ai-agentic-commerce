package io.github.exepex.commerce.shipping.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The settings in {@code application.yml} that the code reads by name. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigKeys {

    public static final String ORDER_EVENTS_TOPIC = "${commerce.topics.order-events}";
    public static final String SHIPMENT_EVENTS_TOPIC = "${commerce.topics.shipment-events}";
}
