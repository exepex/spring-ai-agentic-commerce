package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The request settings every agent sends to Claude, whatever its model and effort. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClaudeSettings {

    /** The most tokens one answer may use, thinking included. */
    public static final int MAX_TOKENS = 16_000;
}
