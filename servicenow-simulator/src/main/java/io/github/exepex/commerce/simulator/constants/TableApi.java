package io.github.exepex.commerce.simulator.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The Table API's request parameters and the keys of its answers, as a real instance names them. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TableApi {

    public static final String QUERY = "sysparm_query";
    public static final String FIELDS = "sysparm_fields";
    public static final String DISPLAY_VALUE = "sysparm_display_value";
    public static final String INPUT_DISPLAY_VALUE = "sysparm_input_display_value";
    public static final String LIMIT = "sysparm_limit";
    public static final String OFFSET = "sysparm_offset";
    public static final String FIELD_SEPARATOR = ",";

    public static final String DEFAULT_LIMIT = "10000";
    public static final String DEFAULT_OFFSET = "0";

    /** The {@code sysparm_display_value} modes: both values, the display value, or (the default) the stored value. */
    public static final String SHOW_ALL = "all";
    public static final String SHOW_DISPLAY = "true";
    public static final String SHOW_STORED = "false";

    public static final String RESULT = "result";
    public static final String VALUE = "value";
    public static final String SHOWN_VALUE = "display_value";
    public static final String LINK = "link";

    public static final String ERROR = "error";
    public static final String MESSAGE = "message";
    public static final String STATUS = "status";
    public static final String FAILURE = "failure";
}
