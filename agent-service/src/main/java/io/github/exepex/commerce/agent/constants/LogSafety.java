package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How values from customers, models and callers are made safe to log. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogSafety {

    /** The characters that would start a new log line or column. */
    public static final String LINE_BREAKS = "[\\r\\n\\t]";
    public static final String REPLACEMENT = "_";
}
