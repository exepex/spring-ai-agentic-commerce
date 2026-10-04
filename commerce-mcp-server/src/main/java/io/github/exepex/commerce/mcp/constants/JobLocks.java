package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The scheduled jobs that run on one instance of the service at a time, by the name of their lock. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JobLocks {

    /** Settles confirmations that did not finish. */
    public static final String PROPOSAL_RECONCILIATION = "proposal-reconciliation";
}
