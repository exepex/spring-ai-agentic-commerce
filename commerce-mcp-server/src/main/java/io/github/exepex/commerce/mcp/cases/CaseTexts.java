package io.github.exepex.commerce.mcp.cases;

import io.github.exepex.commerce.mcp.constants.CaseWording;
import io.github.exepex.commerce.mcp.exception.PendingIncidentStateException;
import io.github.exepex.commerce.mcp.exception.ProblemNotDescribedException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How cases are worded: in ServiceNow, on the order's timeline, and to an agent that must leave the order alone. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CaseTexts {

    private static final int MAX_TEXT_LENGTH = 4000;

    /** Fits ServiceNow's description and work note, however long the details. */
    static String fit(String details) {
        if (details == null || details.isBlank()) {
            throw new ProblemNotDescribedException();
        }
        return details.length() <= MAX_TEXT_LENGTH
                ? details
                : details.substring(0, MAX_TEXT_LENGTH - 1) + CaseWording.TRUNCATED;
    }

    /** The case's incident in brackets, or nothing while it has none. */
    static String incidentOf(SupportCase supportCase) {
        return supportCase.getIncidentNumber() == null
                ? CaseWording.NO_INCIDENT
                : CaseWording.INCIDENT.formatted(supportCase.getIncidentNumber());
    }

    /** Who has the order's open case, for an agent that may not pay on the order while they do. */
    static String holderOf(SupportCase supportCase) {
        return supportCase.getStatus() == SupportCase.Status.WITH_TEAM
                ? CaseWording.WITH_TEAM_HOLDER.formatted(supportCase.getAssignmentGroup(), incidentOf(supportCase))
                : CaseWording.SUPPORT_TEAM_HOLDER.formatted(supportCase.getType(), incidentOf(supportCase));
    }

    /**
     * Who has the case's incident now, for the order's timeline. An incident in ServiceNow is never pending, which
     * {@link CaseService#followIncident} refuses before it gets here.
     */
    static String followed(String incidentNumber, boolean reopened, SupportCase.Status status,
            String assignmentGroup) {
        var incident = incidentNumber + (reopened ? CaseWording.REOPENED_AND : CaseWording.NOT_REOPENED);
        return switch (status) {
            case WITH_AGENT -> CaseWording.WITH_AGENT.formatted(incident);
            case WITH_TEAM -> CaseWording.ASSIGNED.formatted(incident, assignmentGroup);
            case RESOLVED -> CaseWording.RESOLVED.formatted(incident);
            case PENDING -> throw new PendingIncidentStateException();
        };
    }

    /** A resolved incident blocks nobody, so only an open one tells agents to leave the order's money alone. */
    static String describeServiceDeskIncident(String number, SupportCase.Status status, boolean wasResolved) {
        if (status == SupportCase.Status.RESOLVED) {
            return CaseWording.SERVICE_DESK_RESOLVED.formatted(number);
        }
        var change = wasResolved ? CaseWording.SERVICE_DESK_REOPENED : CaseWording.SERVICE_DESK_NOW_ABOUT_ORDER;
        return CaseWording.SERVICE_DESK_OPEN.formatted(number, change);
    }
}
