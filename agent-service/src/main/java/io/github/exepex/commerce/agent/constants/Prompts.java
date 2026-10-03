package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What code adds to the agents' prompts: who the customer is, and which incident to work. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Prompts {

    public static final String SIGNED_IN_CUSTOMER = "\n\nThe signed-in customer is %s.";
    public static final String WORK_INCIDENT = "Work ServiceNow incident %s. It was announced as:\n%s";
}
