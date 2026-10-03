package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How the server's calls are recorded in the shared audit trail: their action and summary. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuditValues {

    /** Prefixes a tool's name, so the trail tells ServiceNow actions from the commerce tools. */
    public static final String ACTION_PREFIX = "servicenow:";

    public static final String NOT_PERMITTED = "%s: tool not permitted for this agent";
    public static final String SWITCHED_OFF = "%s: agent is switched off";
    public static final String NOT_DONE = "%s: %s";

    public static final String READ_INCIDENT = "Read incident %s";
    public static final String ADDED_WORK_NOTE = "Added a work note to %s";
    public static final String LISTED_TEAMS = "Listed the teams";
    public static final String ASSIGNED_TO_TEAM = "Assigned %s to team %s";
    public static final String RESOLVED = "Resolved %s";
    public static final String CLAIMED = "Claimed %s: %s";
    public static final String HANDED_OVER_SWITCHED_OFF = "Handed %s to %s because the agent is switched off";
    public static final String HANDED_OVER_UNFINISHED = "Handed %s to %s because the agent did not finish it";
}
