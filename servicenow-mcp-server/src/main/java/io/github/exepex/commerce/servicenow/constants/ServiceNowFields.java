package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The ServiceNow fields the server reads and writes, by their column names. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceNowFields {

    public static final String SYS_ID = "sys_id";
    public static final String NUMBER = "number";
    public static final String SHORT_DESCRIPTION = "short_description";
    public static final String DESCRIPTION = "description";
    public static final String STATE = "state";
    public static final String ASSIGNMENT_GROUP = "assignment_group";
    public static final String ASSIGNED_TO = "assigned_to";
    public static final String CALLER_ID = "caller_id";
    /** The order an incident is about. */
    public static final String CORRELATION_ID = "correlation_id";
    /** The shop case an incident was opened for. */
    public static final String CORRELATION_DISPLAY = "correlation_display";
    public static final String SYS_CREATED_ON = "sys_created_on";
    public static final String SYS_UPDATED_ON = "sys_updated_on";
    public static final String WORK_NOTES = "work_notes";
    public static final String COMMENTS = "comments";
    public static final String CLOSE_CODE = "close_code";
    public static final String CLOSE_NOTES = "close_notes";

    /** The incident fields every incident read asks for. */
    public static final String INCIDENT_FIELDS = SYS_ID + "," + NUMBER + "," + SHORT_DESCRIPTION + "," + DESCRIPTION
            + "," + STATE + "," + ASSIGNMENT_GROUP + "," + ASSIGNED_TO + "," + CALLER_ID + "," + CORRELATION_ID + ","
            + CORRELATION_DISPLAY + "," + SYS_CREATED_ON + "," + SYS_UPDATED_ON;
    public static final String JOURNAL_FIELDS = WORK_NOTES + "," + COMMENTS;

    /** How ServiceNow writes the times it stores, in UTC. */
    public static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
}
