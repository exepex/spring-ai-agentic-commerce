package io.github.exepex.commerce.payment.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The names and codes payments are recorded and exchanged with: processors, test cards, references and events. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PaymentValues {

    public static final String STRIPE = "stripe";
    public static final String SIMULATED = "simulated";
    public static final String STRIPE_TEST_KEY_PREFIX = "sk_test_";

    public static final String STRIPE_SUCCEEDED = "succeeded";
    public static final String STRIPE_FAILED = "failed";
    public static final String STRIPE_CANCELED = "canceled";

    public static final String DECLINED_CARD = "pm_card_chargeDeclined";
    public static final String REFUND_FAILS_CARD = "pm_card_refundFail";
    public static final String SIMULATED_CHARGE_PREFIX = "sim_pi_";
    public static final String SIMULATED_REFUND_PREFIX = "sim_re_";

    public static final String CHARGE_DESCRIPTION = "Order %s";
    public static final String CHARGE_IDEMPOTENCY_KEY = "charge-%s";
    public static final String REFUND_FAILED_EVENT = "REFUND_FAILED";
}
