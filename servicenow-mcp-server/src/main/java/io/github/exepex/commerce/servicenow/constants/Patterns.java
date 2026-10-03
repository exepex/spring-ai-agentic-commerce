package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The regular expressions values are checked or cleaned with. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Patterns {

    public static final String INCIDENT_NUMBER = "INC\\d{7,}";
    /** A plain sys_id: nothing that could add terms of its own to an encoded query. */
    public static final String SYS_ID = "[A-Za-z0-9-]+";
    public static final String TRAILING_SLASHES = "/+$";
    /** What could start a forged log entry, and what replaces it. */
    public static final String LINE_BREAKS = "[\\r\\n\\t]";
    public static final String LINE_BREAK_REPLACEMENT = "_";
}
