package io.github.exepex.commerce.servicenow.incidents.dto;

import io.github.exepex.commerce.servicenow.constants.IncidentStates;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * An incident as the tools and the poller see it. {@code orderId} is the shop order it is about, taken from its
 * Correlation ID field; empty when it names none. {@code caseId} is the shop's case it was opened for, from its
 * Correlation display field; empty for an incident the service desk raised.
 */
public record Incident(String sysId, String number, String shortDescription, String description, String state,
        String stateName, String assignmentGroup, String assignedToSysId, String assignedTo, String caller,
        String orderId, String caseId, Instant openedAt, Instant updatedAt) {

    /** Resolved, closed and cancelled: the incident needs nothing more. */
    private static final Set<String> STATES_FINISHED =
            Set.of(IncidentStates.RESOLVED, IncidentStates.CLOSED, IncidentStates.CANCELED);
    /** Closed and cancelled: unlike a resolved incident, it can no longer be reopened. */
    private static final Set<String> STATES_FINAL = Set.of(IncidentStates.CLOSED, IncidentStates.CANCELED);

    public boolean isAssigned() {
        return assignedToSysId != null && !assignedToSysId.isBlank();
    }

    /** Resolved, closed or cancelled: the incident needs nothing more. */
    public boolean isFinished() {
        return STATES_FINISHED.contains(state);
    }

    /** Closed or cancelled: unlike a resolved incident, it can no longer be reopened. */
    public boolean isFinal() {
        return STATES_FINAL.contains(state);
    }

    /**
     * Still the agent's claim: assigned to the integration user, in progress, and in the agent's group. A person who
     * moved the incident to another group has it, even if they left it assigned to the agent.
     */
    public boolean isClaimedBy(String integrationUserSysId, String agentGroup) {
        return integrationUserSysId.equals(assignedToSysId) && IncidentStates.IN_PROGRESS.equals(state)
                && agentGroup.equals(assignmentGroup);
    }

    /** The order the incident names now, in its Correlation ID; null when it names none. */
    public UUID linkedOrder() {
        return idOrNull(orderId);
    }

    /** The case the incident was opened for; null for an incident the service desk raised. */
    public UUID openedForCase() {
        return idOrNull(caseId);
    }

    /** The id a Correlation field holds; null when it holds none, or text that is not an id. */
    private static UUID idOrNull(String text) {
        try {
            return text.isBlank() ? null : UUID.fromString(text.strip());
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }
}
