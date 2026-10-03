package io.github.exepex.commerce.simulator.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The ServiceNow fields the simulator reads and writes, by their column names. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FieldNames {

    public static final String SYS_ID = "sys_id";
    public static final String SYS_CREATED_ON = "sys_created_on";
    public static final String SYS_CREATED_BY = "sys_created_by";
    public static final String SYS_UPDATED_ON = "sys_updated_on";
    public static final String NAME = "name";

    public static final String NUMBER = "number";
    public static final String STATE = "state";
    public static final String ASSIGNMENT_GROUP = "assignment_group";
    public static final String ASSIGNED_TO = "assigned_to";
    public static final String CALLER_ID = "caller_id";
    public static final String WORK_NOTES = "work_notes";
    public static final String COMMENTS = "comments";

    public static final String USER_NAME = "user_name";

    /** A journal entry's incident, the journal field it belongs to, and its text. */
    public static final String ELEMENT_ID = "element_id";
    public static final String ELEMENT = "element";
    public static final String VALUE = "value";
}
