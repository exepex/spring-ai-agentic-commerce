package io.github.exepex.commerce.agent;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Values that came from a customer, a model or a caller are logged on one line: a line break in them cannot forge a
 * log entry of its own.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogValues {

    public static String safe(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n\\t]", "_");
    }
}
