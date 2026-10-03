package io.github.exepex.commerce.mcp.cases;

import io.github.exepex.commerce.mcp.governance.GovernanceException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

/** How cases are worded: in ServiceNow, on the order's timeline, and to an agent that must leave the order alone. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CaseTexts {

    private static final int MAX_TEXT_LENGTH = 4000;

    /** Fits ServiceNow's description and work note, however long the details. */
    static String fit(String details) {
        if (details == null || details.isBlank()) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT, "Say what the problem is");
        }
        return details.length() <= MAX_TEXT_LENGTH ? details : details.substring(0, MAX_TEXT_LENGTH - 1) + "…";
    }

    /** The case's incident in brackets, or nothing while it has none. */
    static String incidentOf(SupportCase supportCase) {
        return supportCase.getIncidentNumber() == null ? "" : " (" + supportCase.getIncidentNumber() + ")";
    }

    /** Who has the order's open case, for an agent that may not pay on the order while they do. */
    static String holderOf(SupportCase supportCase) {
        return supportCase.getStatus() == SupportCase.Status.WITH_TEAM
                ? "with the " + supportCase.getAssignmentGroup() + " team in ServiceNow" + incidentOf(supportCase)
                : "an open " + supportCase.getType() + " case" + incidentOf(supportCase) + " that the support team handles";
    }

    /** Who has the case's incident now, for the order's timeline. */
    static String followed(String incidentNumber, boolean reopened, SupportCase.Status status,
            String assignmentGroup) {
        String incident = incidentNumber + (reopened ? " was reopened and" : "");
        return switch (status) {
            case WITH_AGENT -> incident + " is with the incident agent";
            case WITH_TEAM -> incident + " is assigned to " + assignmentGroup;
            case RESOLVED -> incident + " is resolved";
            case PENDING -> throw new IllegalStateException();
        };
    }

    /** A resolved incident blocks nobody, so only an open one tells agents to leave the order's money alone. */
    static String describeServiceDeskIncident(String number, SupportCase.Status status, boolean wasResolved) {
        if (status == SupportCase.Status.RESOLVED) {
            return number + " is now about this order and is resolved";
        }
        String change = wasResolved ? " was reopened" : " is now about this order";
        return number + change + "; agents leave its money to whoever works it";
    }
}
