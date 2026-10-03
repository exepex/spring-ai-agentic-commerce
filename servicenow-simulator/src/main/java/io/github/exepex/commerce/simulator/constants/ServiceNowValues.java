package io.github.exepex.commerce.simulator.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * How a real instance writes what the simulator shows: incident numbers, times, journal entries, people's names and
 * its login.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceNowValues {

    public static final String INCIDENT_NUMBER_PREFIX = "INC";
    public static final String INCIDENT_NUMBER_DIGITS = "%07d";
    /** A sys_id is written as a UUID without its dashes. */
    public static final String UUID_DASH = "-";
    public static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** A journal entry as the incident shows it: its time, its author's name and its kind, then its text. */
    public static final String JOURNAL_ENTRY = "%s - %s (%s)\n%s\n\n";
    public static final String WORK_NOTES_KIND = "Work notes";
    public static final String COMMENTS_KIND = "Additional comments";

    /** What separates the parts of a user name, such as {@code agent.user}. */
    public static final String USER_NAME_SEPARATORS = "[._]";
    public static final String NAME_SEPARATOR = " ";

    public static final String BASIC_AUTH_PREFIX = "Basic ";
    public static final String CREDENTIALS = "%s:%s";
}
