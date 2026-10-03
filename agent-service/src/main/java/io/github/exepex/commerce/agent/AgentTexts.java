package io.github.exepex.commerce.agent;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Text the agents write somewhere with a length limit, such as the audit trail or a ServiceNow note. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AgentTexts {

    /** The text cut to at most {@code maxLength} characters, the cut marked by {@code ellipsis}. */
    static String abbreviate(String text, int maxLength, String ellipsis) {
        return text == null || text.length() <= maxLength
                ? text
                : text.substring(0, maxLength - ellipsis.length()) + ellipsis;
    }
}
