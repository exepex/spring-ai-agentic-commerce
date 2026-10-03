package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Why a tool call is refused, as the agent reads it: each says what to do instead. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RefusalMessages {

    /** Starts every refusal, so a caller can tell one from a failure that asking again may fix. */
    public static final String PREFIX = "Refused: ";

    public static final String TOOL_NOT_PERMITTED = "Agent %s is not permitted to call %s";
    public static final String AGENT_SWITCHED_OFF = "Agent %s is switched off. Stop, and hand the incident to a team "
            + "with " + ToolNames.ASSIGN_TO_TEAM + ".";
    public static final String UNKNOWN_TEAM = "There is no team '%s'. Use one of %s.";
    public static final String SERVICENOW_NOT_CONFIGURED = "ServiceNow is not configured";
    public static final String NOT_AN_INCIDENT_NUMBER = "'%s' is not an incident number such as INC0010001";
    public static final String INCIDENT_NOT_FOUND = "Incident %s does not exist";
    public static final String INCIDENT_NOT_OWNED_ASSIGNED =
            "Incident %s is not yours to change: it is %s and assigned to %s in %s.";
    public static final String INCIDENT_NOT_OWNED_UNASSIGNED =
            "Incident %s is not yours to change: it is %s and unassigned in %s.";
    public static final String EMPTY_NOTE = "The note cannot be empty";
    public static final String NOTE_TOO_LONG = "A note can be at most %s characters";
}
