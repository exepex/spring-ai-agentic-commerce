package io.github.exepex.commerce.payment.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The settings in {@code application.yml} that the code reads by name. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigKeys {

    public static final String PAYMENTS_PREFIX = "commerce.payments";
    public static final String PAYMENT_EVENTS_TOPIC = "${commerce.topics.payment-events}";
    public static final String REFUND_CHECK_INTERVAL = "${commerce.payments.refund-check.interval}";
    public static final String REFUND_CHECK_WATCH = "${commerce.payments.refund-check.watch}";
}
