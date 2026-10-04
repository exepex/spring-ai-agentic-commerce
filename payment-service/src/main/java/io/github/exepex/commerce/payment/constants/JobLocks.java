package io.github.exepex.commerce.payment.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The scheduled jobs that run on one instance of the service at a time, by the name of their lock. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JobLocks {

    /** Checks unsettled refunds with the card processor. */
    public static final String REFUND_CHECK = "refund-check";
}
