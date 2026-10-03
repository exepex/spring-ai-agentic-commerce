package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How the agents' decisions are summarised and explained in the audit trail. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuditTexts {

    public static final String ANSWERED = "Answered %s: %s";
    public static final String CUSTOMER_ASKED = "Customer asked: %s";
    public static final String INCIDENT_WORKED = "Incident %s: %s";
    public static final String TRIGGERED_BY_INCIDENT = "Triggered by ServiceNow incident %s: %s";
    /** Marks where an answer too long for the summary was cut. */
    public static final String ELLIPSIS = "...";
}
