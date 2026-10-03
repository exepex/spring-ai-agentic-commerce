package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import io.github.exepex.commerce.servicenow.constants.AuthValues;
import io.github.exepex.commerce.servicenow.constants.CaseStatuses;
import io.github.exepex.commerce.servicenow.constants.IncidentStates;
import io.github.exepex.commerce.servicenow.constants.IncidentTexts;
import io.github.exepex.commerce.servicenow.constants.ServiceNowFields;
import io.github.exepex.commerce.servicenow.governance.GovernanceApi;
import io.github.exepex.commerce.servicenow.governance.dto.Case;
import io.github.exepex.commerce.servicenow.governance.dto.IncidentLink;
import io.github.exepex.commerce.servicenow.governance.dto.IncidentState;
import io.github.exepex.commerce.servicenow.governance.dto.Note;
import io.github.exepex.commerce.servicenow.governance.dto.ServiceDeskIncident;
import io.github.exepex.commerce.servicenow.incidents.dto.Incident;
import io.github.exepex.commerce.servicenow.security.AgentRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Component
@RequiredArgsConstructor
class CaseSync {

    private final ServiceNowClient serviceNow;
    private final ServiceNowProperties properties;
    private final GovernanceApi governance;
    private final AgentRegistry agents;

    /**
     * Opens the incidents of new cases and sends the notes added to cases since. A case that fails waits for the next
     * poll.
     *
     * @return the cases that failed
     */
    Set<UUID> sendCases() {
        var failed = new HashSet<UUID>();
        for (var outgoing : governance.outgoingCases(authorization())) {
            var supportCase = outgoing.supportCase();
            try {
                var link = supportCase.incidentNumber() == null || supportCase.incidentNumber().isBlank()
                        ? openIncident(supportCase) : supportCase.incidentUrl();
                if (!outgoing.unsentNotes().isEmpty()) {
                    sendNotes(supportCase, link, outgoing.unsentNotes());
                }
            } catch (RuntimeException failure) {
                failed.add(supportCase.id());
                log.warn("Could not send case {} to ServiceNow; trying again next time", supportCase.id(), failure);
            }
        }
        return failed;
    }

    /**
     * Sends the notes as work notes, each ending with its {@link CaseNotes#markerOf marker}. A note whose marker the incident
     * already shows was applied before, though ServiceNow's answer or the shop's confirmation was lost: it is only
     * marked sent, so a retry never adds it twice, even once the incident is resolved. An incident already resolved
     * gets no new notes: nobody reads it any more. The shop learns of the resolution from the read-back and opens a new
     * case for the notes that did not reach it.
     *
     * <p>A case whose incident is not on this instance, such as one opened on an earlier simulator run, gets none:
     * there is nowhere to send them.
     */
    private void sendNotes(Case supportCase, String link, List<Note> notes) {
        var incident = serviceNow.findLinked(link).orElse(null);
        if (incident == null) {
            log.warn("The incident of case {} is not on this instance; its notes are not sent", supportCase.id());
            return;
        }
        var finished = incident.isFinished();
        var workNotes = serviceNow.allWorkNotesOf(incident.sysId());
        for (var note : notes) {
            var applied = workNotes.contains(CaseNotes.markerOf(note));
            if (!applied && finished) {
                continue;
            }
            if (!applied) {
                serviceNow.update(incident.sysId(), Map.of(ServiceNowFields.WORK_NOTES, CaseNotes.workNoteOf(note)));
            }
            governance.markNoteSent(authorization(), supportCase.id(), note.id());
        }
    }

    /**
     * Records each open incident the service desk raised about an order with the shop, which ignores one it already
     * has. An incident whose Correlation display names a case is the shop's own and is left out; any other text there,
     * such as another system's label, does not make it the shop's. An incident whose Correlation ID is not an order id
     * names no order and is left out too. An incident that cannot be recorded is tried next poll.
     *
     * @return the incidents the shop has now, by number, each with the order it has it under
     */
    Map<String, UUID> recordServiceDeskIncidents() {
        var recorded = new HashMap<String, UUID>();
        for (var incident : serviceNow.findOpenWithCorrelationId()) {
            if (!isServiceDeskIncidentAboutAnOrder(incident)) {
                continue;
            }
            var orderId = incident.linkedOrder();
            try {
                var state = stateOf(incident);
                var title = incident.shortDescription().isBlank()
                        ? IncidentTexts.UNTITLED_INCIDENT.formatted(incident.number()) : incident.shortDescription();
                governance.recordServiceDeskIncident(authorization(), new ServiceDeskIncident(orderId,
                        incident.number(), serviceNow.linkTo(incident), title, state.status(), state.assignmentGroup()));
                recorded.put(incident.number(), orderId);
            } catch (RuntimeException failure) {
                log.warn("Could not record incident {} with the shop; trying again next time", incident.number(), failure);
            }
        }
        return recorded;
    }

    /** Whether the service desk raised the incident about an order: it names an order, and no case of the shop's. */
    boolean isServiceDeskIncidentAboutAnOrder(Incident incident) {
        return incident.linkedOrder() != null && incident.openedForCase() == null;
    }

    /** Whether the shop has the incident, as recorded this poll, under the order the incident names now. */
    static boolean isRecordedForItsOrder(Incident incident, Map<String, UUID> recorded) {
        var orderId = incident.linkedOrder();
        return orderId != null && orderId.equals(recorded.get(incident.number()));
    }

    /**
     * Reads who has each case's incident now and tells the shop. Each incident is found by the case's link, so a case
     * whose incident is on another instance is left alone, even if this instance has an incident with its number. An
     * incident that cannot be read is tried next poll.
     *
     * <p>A case's resolution is reported only once its notes are settled: a note may have reached its incident though
     * ServiceNow's answer was lost, and the shop carries a resolved case's unsent notes over to a new case. The next
     * poll finds the note's marker on the incident and marks it sent first. Every other change of owner is reported
     * at once.
     *
     * @param notesSettled whether a case's notes are settled, so its resolution may be reported
     */
    void readBackIncidents(Predicate<UUID> notesSettled) {
        for (var supportCase : governance.casesInServiceNow(authorization())) {
            try {
                serviceNow.findLinked(supportCase.incidentUrl()).ifPresentOrElse(
                        incident -> {
                            var state = stateOf(incident);
                            if (CaseStatuses.RESOLVED.equals(state.status()) && !notesSettled.test(supportCase.id())) {
                                log.info("{} is resolved; telling the shop once its notes are settled", incident.number());
                                return;
                            }
                            report(supportCase.id(), state);
                        },
                        () -> log.warn("Incident {} of case {} is not on this instance", supportCase.incidentNumber(),
                                supportCase.id()));
            } catch (RuntimeException failure) {
                log.warn("Could not read incident {} back; trying again next time", supportCase.incidentNumber(), failure);
            }
        }
    }

    /**
     * Tells the shop at once that a case's incident went to a team, so agents leave the order to that team without
     * waiting for the next poll. If the shop cannot be told now, the next read-back tells it.
     */
    void reportHandedToTeam(Incident incident, String group) {
        var caseId = incident.openedForCase();
        if (caseId == null) {
            return;
        }
        try {
            report(caseId, new IncidentState(incident.number(), CaseStatuses.WITH_TEAM, group, false,
                    incident.linkedOrder()));
        } catch (RuntimeException failure) {
            log.warn("Could not tell the shop that {} went to {}; the next poll will", incident.number(), group, failure);
        }
    }

    /** Opens the case's incident, or finds the one opened before, links the case to it, and returns the link. */
    private String openIncident(Case supportCase) {
        var incident = serviceNow.findByCaseId(supportCase.id()).orElse(null);
        if (incident == null) {
            var fields = new LinkedHashMap<String, String>();
            fields.put(ServiceNowFields.ASSIGNMENT_GROUP, supportCase.isForPeople()
                    ? properties.teams().get(properties.defaultTeam()).group()
                    : properties.agentGroup());
            fields.put(ServiceNowFields.SHORT_DESCRIPTION, supportCase.title());
            fields.put(ServiceNowFields.DESCRIPTION, supportCase.description());
            fields.put(ServiceNowFields.CORRELATION_ID,
                    supportCase.orderId() == null ? "" : supportCase.orderId().toString());
            fields.put(ServiceNowFields.CORRELATION_DISPLAY, supportCase.id().toString());
            incident = serviceNow.create(fields);
        }
        var link = serviceNow.linkTo(incident);
        governance.linkIncident(authorization(), supportCase.id(), new IncidentLink(incident.number(), link));
        return link;
    }

    private IncidentState stateOf(Incident incident) {
        var orderId = incident.linkedOrder();
        if (incident.isFinished()) {
            return new IncidentState(incident.number(), CaseStatuses.RESOLVED, incident.assignmentGroup(),
                    incident.isFinal(), orderId);
        }
        var takenByAPerson = incident.isAssigned()
                && !serviceNow.integrationUserSysId().equals(incident.assignedToSysId());
        // The agent only claims new incidents and only works ones in progress; in any other state, such as On Hold, a
        // person put it there and has it.
        var workableByTheAgent = IncidentStates.NEW.equals(incident.state())
                || IncidentStates.IN_PROGRESS.equals(incident.state());
        if (properties.agentGroup().equals(incident.assignmentGroup()) && !takenByAPerson && workableByTheAgent) {
            return new IncidentState(incident.number(), CaseStatuses.WITH_AGENT, incident.assignmentGroup(), false,
                    orderId);
        }
        // A person who took the incident has it, even while it is still in the agent's group.
        var group = incident.assignmentGroup().isBlank() ? IncidentTexts.NO_GROUP : incident.assignmentGroup();
        var owner = ownerOf(incident, group, takenByAPerson, workableByTheAgent);
        return new IncidentState(incident.number(), CaseStatuses.WITH_TEAM, owner, false, orderId);
    }

    /** Who has the incident: the person who took it, the group, or the group and the state keeping the agent out. */
    private static String ownerOf(Incident incident, String group, boolean takenByAPerson, boolean workableByTheAgent) {
        if (takenByAPerson) {
            return IncidentTexts.GROUP_WITH_DETAIL.formatted(group, incident.assignedTo());
        }
        if (workableByTheAgent) {
            return group;
        }
        return IncidentTexts.GROUP_WITH_DETAIL.formatted(group, incident.stateName());
    }

    private void report(UUID caseId, IncidentState state) {
        governance.followIncident(authorization(), caseId, state);
    }

    private String authorization() {
        return AuthValues.BEARER_PREFIX + agents.tokenOf(properties.agent());
    }
}
