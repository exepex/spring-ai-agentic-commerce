package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.RefusalMessages;
import io.github.exepex.commerce.servicenow.incidents.dto.Incident;

/** The incident is not the agent's claim: a person or another team has it, or the agent handed it over. */
public class IncidentNotOwnedException extends ToolRefusedException {

    public IncidentNotOwnedException(Incident incident) {
        super(incident.isAssigned()
                ? RefusalMessages.INCIDENT_NOT_OWNED_ASSIGNED.formatted(incident.number(), incident.stateName(),
                        incident.assignedTo(), incident.assignmentGroup())
                : RefusalMessages.INCIDENT_NOT_OWNED_UNASSIGNED.formatted(incident.number(), incident.stateName(),
                        incident.assignmentGroup()));
    }
}
