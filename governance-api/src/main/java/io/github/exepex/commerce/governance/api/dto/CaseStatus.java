package io.github.exepex.commerce.governance.api.dto;

/** Who has a case's incident. */
public enum CaseStatus {
    /** Recorded; its incident is not in ServiceNow yet. */
    PENDING,
    /** Its incident is in ServiceNow, with the incident agent. */
    WITH_AGENT,
    /** Its incident is with a team or a person. */
    WITH_TEAM,
    /** Its incident is resolved. */
    RESOLVED
}
