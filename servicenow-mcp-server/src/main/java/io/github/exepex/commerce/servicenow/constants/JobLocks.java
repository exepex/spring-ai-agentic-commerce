package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The scheduled jobs that run on one instance of the service at a time, by the name of their lock. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JobLocks {

    /** Sends cases, claims and hands over incidents, and reads them back. */
    public static final String INCIDENT_POLL = "incident-poll";
}
