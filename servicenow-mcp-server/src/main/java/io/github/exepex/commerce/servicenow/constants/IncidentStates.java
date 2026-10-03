package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The codes ServiceNow stores for an incident's states. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IncidentStates {

    public static final String NEW = "1";
    public static final String IN_PROGRESS = "2";
    public static final String RESOLVED = "6";
    public static final String CLOSED = "7";
    public static final String CANCELED = "8";
    /** Resolved, closed and cancelled, as an encoded query lists them: the incident needs nothing more. */
    public static final String FINISHED = RESOLVED + "," + CLOSED + "," + CANCELED;
}
