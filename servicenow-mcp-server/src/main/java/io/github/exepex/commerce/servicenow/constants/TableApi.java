package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** ServiceNow's Table API as the server calls it: its addresses, request parameters and the keys of its answers. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TableApi {

    public static final String INCIDENTS = "/api/now/table/incident";
    public static final String INCIDENT = INCIDENTS + "/{sysId}";
    public static final String USERS = "/api/now/table/sys_user";
    /** Follows the instance's URL in the link where a person opens an incident; the incident's sys_id ends it. */
    public static final String INCIDENT_LINK = "/incident.do?sys_id=";
    /** The base URL while no instance is configured, which no request can reach. */
    public static final String NO_INSTANCE = "http://servicenow.invalid";

    public static final String QUERY = "sysparm_query";
    public static final String FIELDS = "sysparm_fields";
    public static final String DISPLAY_VALUE = "sysparm_display_value";
    public static final String INPUT_DISPLAY_VALUE = "sysparm_input_display_value";
    public static final String LIMIT = "sysparm_limit";
    public static final String OFFSET = "sysparm_offset";
    /** The {@code sysparm_display_value} that answers each field with its stored and its display value. */
    public static final String SHOW_ALL = "all";

    public static final String RESULT = "result";
    public static final String VALUE = "value";
    public static final String SHOWN_VALUE = "display_value";
}
