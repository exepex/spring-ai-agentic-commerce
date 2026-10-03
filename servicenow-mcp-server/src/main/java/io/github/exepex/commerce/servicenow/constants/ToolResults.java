package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the ServiceNow tools tell the agent once they have done what it asked. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ToolResults {

    public static final String WORK_NOTE_ADDED = "The work note was added";
    public static final String ASSIGNED_TO_TEAM = "Assigned to %s, who are notified by ServiceNow";
    public static final String INCIDENT_RESOLVED = "The incident is resolved";
    /** Ends a journal field cut to its newest part. */
    public static final String OLDER_ENTRIES_LEFT_OUT = "\n[Older entries left out.]";
}
