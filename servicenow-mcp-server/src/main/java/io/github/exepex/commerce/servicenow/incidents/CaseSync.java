package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import io.github.exepex.commerce.servicenow.governance.GovernanceApi;
import io.github.exepex.commerce.servicenow.security.AgentRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Keeps the shop's cases and their ServiceNow incidents in step, for the agent that works cases. It opens an incident
 * in the agent's group for each new case, sends what was added to a case later as work notes, and tells the shop who
 * has each incident now: the agent, a team, or nobody because it is resolved.
 *
 * <p>An incident carries its case's id in its Correlation display field, so a case whose incident was opened but not
 * yet reported to the shop, because the poller stopped in between, is found again instead of opened twice.
 */
@Component
class CaseSync {

    static final String WITH_AGENT = "WITH_AGENT";
    static final String WITH_TEAM = "WITH_TEAM";
    static final String RESOLVED = "RESOLVED";

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseSync.class);

    private final ServiceNowClient serviceNow;
    private final ServiceNowProperties properties;
    private final GovernanceApi governance;
    private final AgentRegistry agents;

    CaseSync(ServiceNowClient serviceNow, ServiceNowProperties properties, GovernanceApi governance, AgentRegistry agents) {
        this.serviceNow = serviceNow;
        this.properties = properties;
        this.governance = governance;
        this.agents = agents;
    }

    /** Opens the incidents of new cases and sends the notes added to cases since. A case that fails waits for the next poll. */
    void sendCases() {
        for (GovernanceApi.OutgoingCase outgoing : governance.outgoingCases(authorization())) {
            GovernanceApi.Case supportCase = outgoing.supportCase();
            try {
                String number = supportCase.incidentNumber();
                if (number == null || number.isBlank()) {
                    number = openIncident(supportCase);
                }
                if (!outgoing.unsentNotes().isEmpty()) {
                    sendNotes(supportCase, number, outgoing.unsentNotes());
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not send case {} to ServiceNow; trying again next time", supportCase.id(), failure);
            }
        }
    }

    /**
     * Sends the notes as work notes. An incident already resolved gets none: nobody reads it any more. The shop learns
     * of the resolution from the read-back and opens a new case for the notes that did not reach it.
     */
    private void sendNotes(GovernanceApi.Case supportCase, String number, List<GovernanceApi.Note> notes) {
        ServiceNowClient.Incident incident = serviceNow.findByNumber(number)
                .orElseThrow(() -> new IllegalStateException("Incident " + number + " is gone"));
        if (ServiceNowClient.STATES_FINISHED.contains(incident.state())) {
            return;
        }
        for (GovernanceApi.Note note : notes) {
            serviceNow.update(incident.sysId(), Map.of("work_notes", note.text()));
            governance.markNoteSent(authorization(), supportCase.id(), note.id());
        }
    }

    /** Reads who has each case's incident now and tells the shop. An incident that cannot be read is tried next poll. */
    void readBackIncidents() {
        for (GovernanceApi.Case supportCase : governance.casesInServiceNow(authorization())) {
            try {
                serviceNow.findByNumber(supportCase.incidentNumber()).ifPresentOrElse(
                        incident -> report(supportCase.id(), stateOf(incident)),
                        () -> LOGGER.warn("Incident {} of case {} is not in ServiceNow", supportCase.incidentNumber(),
                                supportCase.id()));
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not read incident {} back; trying again next time", supportCase.incidentNumber(), failure);
            }
        }
    }

    /**
     * Tells the shop at once that a case's incident went to a team, so agents leave the order to that team without
     * waiting for the next poll. If the shop cannot be told now, the next read-back tells it.
     */
    void reportHandedToTeam(ServiceNowClient.Incident incident, String group) {
        UUID caseId = caseIdOf(incident);
        if (caseId == null) {
            return;
        }
        try {
            report(caseId, new GovernanceApi.IncidentState(incident.number(), WITH_TEAM, group));
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not tell the shop that {} went to {}; the next poll will", incident.number(), group, failure);
        }
    }

    private String openIncident(GovernanceApi.Case supportCase) {
        ServiceNowClient.Incident incident = serviceNow.findByCaseId(supportCase.id()).orElse(null);
        if (incident == null) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("assignment_group", properties.agentGroup());
            fields.put("short_description", supportCase.title());
            fields.put("description", supportCase.description());
            fields.put("correlation_id", supportCase.orderId() == null ? "" : supportCase.orderId().toString());
            fields.put("correlation_display", supportCase.id().toString());
            incident = serviceNow.create(fields);
        }
        governance.linkIncident(authorization(), supportCase.id(),
                new GovernanceApi.IncidentLink(incident.number(), serviceNow.linkTo(incident)));
        return incident.number();
    }

    private GovernanceApi.IncidentState stateOf(ServiceNowClient.Incident incident) {
        if (ServiceNowClient.STATES_FINISHED.contains(incident.state())) {
            return new GovernanceApi.IncidentState(incident.number(), RESOLVED, incident.assignmentGroup());
        }
        boolean takenByAPerson = incident.isAssigned()
                && !serviceNow.integrationUserSysId().equals(incident.assignedToSysId());
        if (properties.agentGroup().equals(incident.assignmentGroup()) && !takenByAPerson) {
            return new GovernanceApi.IncidentState(incident.number(), WITH_AGENT, incident.assignmentGroup());
        }
        // A person who took the incident has it, even while it is still in the agent's group.
        String group = incident.assignmentGroup().isBlank() ? "no group" : incident.assignmentGroup();
        String owner = takenByAPerson ? group + " (" + incident.assignedTo() + ")" : group;
        return new GovernanceApi.IncidentState(incident.number(), WITH_TEAM, owner);
    }

    private void report(UUID caseId, GovernanceApi.IncidentState state) {
        governance.followIncident(authorization(), caseId, state);
    }

    /** The case the incident was opened for; null for an incident the service desk raised. */
    private static UUID caseIdOf(ServiceNowClient.Incident incident) {
        try {
            return incident.caseId().isBlank() ? null : UUID.fromString(incident.caseId());
        } catch (IllegalArgumentException notACase) {
            return null;
        }
    }

    private String authorization() {
        return "Bearer " + agents.tokenOf(properties.agent());
    }
}
