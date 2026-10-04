package io.github.exepex.commerce.order.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The scheduled jobs that run on one instance of the service at a time, by the name of their lock. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JobLocks {

    /** Settles stalled checkouts and retries stock releases. */
    public static final String ORDER_RECONCILIATION = "order-reconciliation";
}
