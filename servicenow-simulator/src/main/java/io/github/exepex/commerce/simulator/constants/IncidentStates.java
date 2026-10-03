package io.github.exepex.commerce.simulator.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** An incident's states: the code stored on it and the name a person sees. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IncidentStates {

    public static final String NEW = "1";
    public static final String IN_PROGRESS = "2";
    public static final String ON_HOLD = "3";
    public static final String RESOLVED = "6";
    public static final String CLOSED = "7";
    public static final String CANCELED = "8";

    public static final String NEW_NAME = "New";
    public static final String IN_PROGRESS_NAME = "In Progress";
    public static final String ON_HOLD_NAME = "On Hold";
    public static final String RESOLVED_NAME = "Resolved";
    public static final String CLOSED_NAME = "Closed";
    public static final String CANCELED_NAME = "Canceled";
}
