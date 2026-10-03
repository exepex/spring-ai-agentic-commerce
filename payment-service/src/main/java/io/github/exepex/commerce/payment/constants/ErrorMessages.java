package io.github.exepex.commerce.payment.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the payment service tells its callers when it cannot do what they asked. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String PAYMENT_NOT_FOUND = "Order %s has no payment";
    public static final String CHARGE_CONFLICTS =
            "Order %s was already charged with a different customer, amount or currency";
    public static final String REFUND_EXCEEDS_PAYMENT = "Refund of %s exceeds the %s still refundable";
    public static final String REFUND_NOT_COMPLETED =
            "The card processor did not complete the refund (status %s); no money was returned";
    public static final String IDEMPOTENCY_KEY_REUSED = "Idempotency key %s was already used for a different refund";
    public static final String PROVIDER_UNAVAILABLE = "The card processor did not respond; try again";
    public static final String CARD_DECLINED = "Your card was declined.";
    public static final String PAYMENT_ENDED_IN_STATUS = "Payment ended in status %s";
    public static final String STRIPE_TEST_KEY_REQUIRED = "This demo only accepts a Stripe test-mode key (sk_test_...)";
    public static final String SIMULATED_OUTAGE = "{\"status\": 503, \"title\": \"Service Unavailable\", "
            + "\"detail\": \"The payment service is down (simulated outage)\"}";
}
