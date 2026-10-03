package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import io.github.exepex.commerce.servicenow.governance.GovernanceApi;
import io.github.exepex.commerce.servicenow.security.AgentRegistry;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Keeps the shop's cases and their ServiceNow incidents in step, for the agent that works cases. It opens an incident
 * for each new case, in the agent's group or, for a case already meant for people, the default team's; sends what was
 * added to a case later as work notes; and tells the shop who has each incident now: the agent, a team, or nobody
 * because it is resolved.
 *
 * <p>An incident carries its case's id in its Correlation display field, so a case whose incident was opened but not
 * yet reported to the shop, because the poller stopped in between, is found again instead of opened twice.
 *
 * <p>An open incident the service desk raised about an order is recorded with the shop as a case of its own, and from
 * then on read back like the others, so agents leave the order's money to whoever works it.
 */
@Component
class CaseSync {

    static final String WITH_AGENT = "WITH_AGENT";
    static final String WITH_TEAM = "WITH_TEAM";
    static final String RESOLVED = "RESOLVED";

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseSync.class);
    /** The longest work note sent, as for the agent's own notes: a case's note can be this long before its marker. */
    private static final int MAX_WORK_NOTE_LENGTH = 4000;

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

    /**
     * Opens the incidents of new cases and sends the notes added to cases since. A case that fails waits for the next
     * poll.
     *
     * @return the cases that failed
     */
    Set<UUID> sendCases() {
        Set<UUID> failed = new HashSet<>();
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
                failed.add(supportCase.id());
                LOGGER.warn("Could not send case {} to ServiceNow; trying again next time", supportCase.id(), failure);
            }
        }
        return failed;
    }

    /**
     * Sends the notes as work notes, each ending with its {@link #markerOf marker}. A note whose marker the incident
     * already shows was applied before, though ServiceNow's answer or the shop's confirmation was lost: it is only
     * marked sent, so a retry never adds it twice, even once the incident is resolved. An incident already resolved
     * gets no new notes: nobody reads it any more. The shop learns of the resolution from the read-back and opens a new
     * case for the notes that did not reach it.
     */
    private void sendNotes(GovernanceApi.Case supportCase, String number, List<GovernanceApi.Note> notes) {
        ServiceNowClient.Incident incident = serviceNow.findByNumber(number)
                .orElseThrow(() -> new IllegalStateException("Incident " + number + " is gone"));
        boolean finished = ServiceNowClient.STATES_FINISHED.contains(incident.state());
        String workNotes = serviceNow.allWorkNotesOf(incident.sysId());
        for (GovernanceApi.Note note : notes) {
            boolean applied = workNotes.contains(markerOf(note));
            if (!applied && finished) {
                continue;
            }
            if (!applied) {
                serviceNow.update(incident.sysId(), Map.of("work_notes", workNoteOf(note)));
            }
            governance.markNoteSent(authorization(), supportCase.id(), note.id());
        }
    }

    /** The note as a work note: its text, shortened if need be so that its marker always fits within the limit. */
    private static String workNoteOf(GovernanceApi.Note note) {
        String ending = "\n\n" + markerOf(note);
        String text = note.text();
        int room = MAX_WORK_NOTE_LENGTH - ending.length();
        return (text.length() <= room ? text : text.substring(0, room)) + ending;
    }

    /** The line that ends a case note's work note, so the incident shows which notes it already holds. */
    private static String markerOf(GovernanceApi.Note note) {
        return "[shop note " + note.id() + "]";
    }

    /**
     * Records each open incident the service desk raised about an order with the shop, which ignores one it already
     * has. An incident whose Correlation display names a case is the shop's own and is left out; any other text there,
     * such as another system's label, does not make it the shop's. An incident whose Correlation ID is not an order id
     * names no order and is left out too. An incident that cannot be recorded is tried next poll.
     *
     * @return the numbers of the incidents the shop has now
     */
    Set<String> recordServiceDeskIncidents() {
        Set<String> recorded = new HashSet<>();
        for (ServiceNowClient.Incident incident : serviceNow.findOpenWithCorrelationId()) {
            if (!isServiceDeskIncidentAboutAnOrder(incident)) {
                continue;
            }
            UUID orderId = uuidOrNull(incident.orderId());
            try {
                GovernanceApi.IncidentState state = stateOf(incident);
                String title = incident.shortDescription().isBlank() ? "Incident " + incident.number()
                        : incident.shortDescription();
                governance.recordServiceDeskIncident(authorization(), new GovernanceApi.ServiceDeskIncident(orderId,
                        incident.number(), serviceNow.linkTo(incident), title, state.status(), state.assignmentGroup()));
                recorded.add(incident.number());
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not record incident {} with the shop; trying again next time", incident.number(), failure);
            }
        }
        return recorded;
    }

    /** Whether the service desk raised the incident about an order: it names an order, and no case of the shop's. */
    boolean isServiceDeskIncidentAboutAnOrder(ServiceNowClient.Incident incident) {
        return uuidOrNull(incident.orderId()) != null && caseIdOf(incident) == null;
    }

    /**
     * Reads who has each case's incident now and tells the shop. An incident that cannot be read is tried next poll.
     *
     * <p>A case's resolution is reported only once its notes are settled: a note may have reached its incident though
     * ServiceNow's answer was lost, and the shop carries a resolved case's unsent notes over to a new case. The next
     * poll finds the note's marker on the incident and marks it sent first. Every other change of owner is reported
     * at once.
     *
     * @param notesSettled whether a case's notes are settled, so its resolution may be reported
     */
    void readBackIncidents(Predicate<UUID> notesSettled) {
        for (GovernanceApi.Case supportCase : governance.casesInServiceNow(authorization())) {
            try {
                serviceNow.findByNumber(supportCase.incidentNumber()).ifPresentOrElse(
                        incident -> {
                            GovernanceApi.IncidentState state = stateOf(incident);
                            if (RESOLVED.equals(state.status()) && !notesSettled.test(supportCase.id())) {
                                LOGGER.info("{} is resolved; telling the shop once its notes are settled", incident.number());
                                return;
                            }
                            report(supportCase.id(), state);
                        },
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
            fields.put("assignment_group", supportCase.isForPeople()
                    ? properties.teams().get(properties.defaultTeam()).group()
                    : properties.agentGroup());
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
        // The agent only claims new incidents and only works ones in progress; in any other state, such as On Hold, a
        // person put it there and has it.
        boolean workableByTheAgent = ServiceNowClient.STATE_NEW.equals(incident.state())
                || ServiceNowClient.STATE_IN_PROGRESS.equals(incident.state());
        if (properties.agentGroup().equals(incident.assignmentGroup()) && !takenByAPerson && workableByTheAgent) {
            return new GovernanceApi.IncidentState(incident.number(), WITH_AGENT, incident.assignmentGroup());
        }
        // A person who took the incident has it, even while it is still in the agent's group.
        String group = incident.assignmentGroup().isBlank() ? "no group" : incident.assignmentGroup();
        String owner = takenByAPerson ? group + " (" + incident.assignedTo() + ")"
                : workableByTheAgent ? group : group + " (" + incident.stateName() + ")";
        return new GovernanceApi.IncidentState(incident.number(), WITH_TEAM, owner);
    }

    private void report(UUID caseId, GovernanceApi.IncidentState state) {
        governance.followIncident(authorization(), caseId, state);
    }

    /** The case the incident was opened for; null for an incident the service desk raised. */
    private static UUID caseIdOf(ServiceNowClient.Incident incident) {
        return uuidOrNull(incident.caseId());
    }

    private static UUID uuidOrNull(String text) {
        try {
            return text.isBlank() ? null : UUID.fromString(text.strip());
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    private String authorization() {
        return "Bearer " + agents.tokenOf(properties.agent());
    }
}
