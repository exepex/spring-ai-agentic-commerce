package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The encoded queries the server finds incidents and users with. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IncidentQueries {

    public static final String BY_NUMBER = "number=%s";
    public static final String BY_SYS_ID = "sys_id=%s";
    public static final String BY_CASE = "correlation_display=%s";
    /** New incidents in a group that nobody has taken yet. */
    public static final String NEW_IN_GROUP = "assignment_group.name=%s^assigned_toISEMPTY^state=" + IncidentStates.NEW;
    /** Incidents in a group assigned to a user and in progress. */
    public static final String IN_PROGRESS_IN_GROUP_WITH =
            "assignment_group.name=%s^assigned_to=%s^state=" + IncidentStates.IN_PROGRESS;
    /** Open incidents that name something in their Correlation ID, oldest first. */
    public static final String OPEN_WITH_CORRELATION_ID =
            "correlation_idISNOTEMPTY^stateNOT IN" + IncidentStates.FINISHED + "^ORDERBYsys_created_on";
    public static final String USER_BY_NAME = "user_name=%s";
}
