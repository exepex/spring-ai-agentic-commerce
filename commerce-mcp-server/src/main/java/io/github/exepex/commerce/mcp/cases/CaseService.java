package io.github.exepex.commerce.mcp.cases;

import io.github.exepex.commerce.mcp.governance.AuditEvent;
import io.github.exepex.commerce.mcp.governance.AuditTrail;
import io.github.exepex.commerce.mcp.governance.GovernanceException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens a case for every problem that needs handling, and keeps it in step with its ServiceNow incident. Code opens
 * cases, never a model's judgement: a stock-out, a delivery that failed, a lost parcel, a refund that failed at the
 * card processor, and every hand-off an agent asks for.
 *
 * <p>An order has at most one unresolved case of each type, so the same problem raised again adds a note to the open
 * case instead of opening a second one. The ServiceNow MCP server's poller carries cases and notes to ServiceNow and
 * reports back who has each incident.
 */
@Service
public class CaseService {

    /** The actor recorded for what ServiceNow did, as reported by its poller. */
    static final String SERVICENOW = "servicenow";

    private static final int MAX_TEXT_LENGTH = 4000;
    private static final List<SupportCase.Status> UNRESOLVED = List.of(SupportCase.Status.PENDING,
            SupportCase.Status.WITH_AGENT, SupportCase.Status.WITH_TEAM);
    private static final List<SupportCase.Status> IN_SERVICENOW = List.of(SupportCase.Status.WITH_AGENT,
            SupportCase.Status.WITH_TEAM);

    /** A case with what is still to be sent to ServiceNow: its incident, when it has none, and its unsent notes. */
    public record Outgoing(SupportCase supportCase, List<CaseNote> unsentNotes) {}

    private final SupportCaseRepository cases;
    private final CaseNoteRepository notes;
    private final AuditTrail audit;
    private final JdbcClient jdbc;
    private final Clock clock;

    CaseService(SupportCaseRepository cases, CaseNoteRepository notes, AuditTrail audit, JdbcClient jdbc, Clock clock) {
        this.cases = cases;
        this.notes = notes;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Opens a case, or adds {@code details} to the order's open case of the same type. The case, its note and its
     * audit entry are saved together or not at all.
     *
     * @param orderId the order the problem is about; null for a problem that is about no single order, which always
     *     opens a case of its own
     */
    @Transactional
    public SupportCase raise(CaseType type, UUID orderId, String details, AuditEvent.ActorType raisedByType,
            String raisedBy) {
        return openOrAddTo(type, orderId, details, raisedByType, raisedBy);
    }

    /**
     * Raises the case for an event another service announced, once per order however often Kafka delivers the event.
     */
    @Transactional
    public void raiseFor(UUID sourceEventId, CaseType type, UUID orderId, String details, String service) {
        int firstTime = jdbc.sql("insert into governance.case_event (source_event_id, order_id) values (:eventId, :orderId) "
                        + "on conflict do nothing")
                .param("eventId", sourceEventId)
                .param("orderId", orderId)
                .update();
        if (firstTime == 1) {
            openOrAddTo(type, orderId, details, AuditEvent.ActorType.SYSTEM, service);
        }
    }

    /** Opens the case or adds to the open one, within the caller's transaction. */
    private SupportCase openOrAddTo(CaseType type, UUID orderId, String details, AuditEvent.ActorType raisedByType,
            String raisedBy) {
        String text = fit(details);
        Instant now = Instant.now(clock);
        if (orderId != null) {
            // Two raises of the same problem at once must not each miss the other's case.
            jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 2))")
                    .param("key", orderId + "/" + type)
                    .query((row, number) -> number)
                    .single();
            Optional<SupportCase> open = cases.findByOrderIdAndTypeAndStatusNot(orderId, type,
                    SupportCase.Status.RESOLVED);
            if (open.isPresent()) {
                notes.save(new CaseNote(open.get().getId(), text, now));
                audit.record(orderId, raisedByType, raisedBy, "raise_case", AuditEvent.Outcome.SUCCEEDED,
                        "Added to the open " + type + " case" + incidentOf(open.get()), text);
                return open.get();
            }
        }
        SupportCase opened = cases.save(new SupportCase(orderId, type, text, raisedBy, now));
        audit.record(orderId, raisedByType, raisedBy, "raise_case", AuditEvent.Outcome.SUCCEEDED,
                "Opened a " + type + " case; it goes to ServiceNow as an incident for the incident agent", text);
        return opened;
    }

    /**
     * Agents leave an order alone while a team has one of its incidents: the people working it may be paying the
     * customer back another way.
     */
    public void ensureNotWithTeam(UUID orderId) {
        Optional<SupportCase> withTeam = cases.findByOrderIdAndStatus(orderId, SupportCase.Status.WITH_TEAM).stream()
                .findFirst();
        if (withTeam.isPresent()) {
            throw new GovernanceException(HttpStatus.CONFLICT, "This order is with the "
                    + withTeam.get().getAssignmentGroup() + " team in ServiceNow" + incidentOf(withTeam.get())
                    + ", who will finish it. Do not retry; tell the customer a person is looking into it.");
        }
    }

    /** Cases whose incident is still to be created, and cases with notes still to be sent, oldest first. */
    @Transactional(readOnly = true)
    public List<Outgoing> toSend() {
        Map<UUID, List<CaseNote>> unsentByCase = new LinkedHashMap<>();
        for (CaseNote note : notes.findBySentAtIsNullOrderByCreatedAt()) {
            unsentByCase.computeIfAbsent(note.getCaseId(), caseId -> new ArrayList<>()).add(note);
        }
        Map<UUID, SupportCase> outgoing = new LinkedHashMap<>();
        cases.findByStatusInOrderByCreatedAt(List.of(SupportCase.Status.PENDING))
                .forEach(pending -> outgoing.put(pending.getId(), pending));
        cases.findByIdIn(unsentByCase.keySet()).forEach(withNotes -> outgoing.putIfAbsent(withNotes.getId(), withNotes));
        return outgoing.values().stream()
                .map(supportCase -> new Outgoing(supportCase, unsentByCase.getOrDefault(supportCase.getId(), List.of())))
                .toList();
    }

    /** Records the incident ServiceNow created for the case. Reporting it again changes nothing. */
    @Transactional
    public SupportCase linkIncident(UUID caseId, String number, String url) {
        SupportCase supportCase = find(caseId);
        if (supportCase.linkIncident(number, url, Instant.now(clock))) {
            audit.record(supportCase.getOrderId(), AuditEvent.ActorType.SYSTEM, SERVICENOW, "open_incident",
                    AuditEvent.Outcome.SUCCEEDED, "Opened ServiceNow incident " + number + " for the "
                            + supportCase.getType() + " case", null);
        }
        return supportCase;
    }

    @Transactional
    public void markNoteSent(UUID caseId, UUID noteId) {
        notes.findById(noteId)
                .filter(note -> note.getCaseId().equals(caseId))
                .orElseThrow(() -> new GovernanceException(HttpStatus.NOT_FOUND,
                        "Case " + caseId + " has no note " + noteId))
                .markSent(Instant.now(clock));
    }

    /** Cases whose incident is in ServiceNow and not resolved: the poller reads who has each one now. */
    public List<SupportCase> inServiceNow() {
        return cases.findByStatusInOrderByCreatedAt(IN_SERVICENOW);
    }

    /**
     * Takes over who has the case's incident: the agent, a team, or nobody any more because it is resolved. Recorded
     * on the order's timeline when it changes. Only the case's own incident counts: an incident whose Correlation
     * display field was edited to name another case cannot change that case.
     */
    @Transactional
    public SupportCase followIncident(UUID caseId, String number, SupportCase.Status status, String assignmentGroup) {
        if (status == SupportCase.Status.PENDING) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT, "An incident in ServiceNow is not pending");
        }
        SupportCase supportCase = find(caseId);
        if (!number.equals(supportCase.getIncidentNumber())) {
            throw new GovernanceException(HttpStatus.CONFLICT, "Incident " + number + " is not the incident of case "
                    + caseId);
        }
        if (supportCase.followIncident(status, assignmentGroup, Instant.now(clock))) {
            String incident = supportCase.getIncidentNumber();
            String summary = switch (status) {
                case WITH_AGENT -> incident + " is with the incident agent";
                case WITH_TEAM -> incident + " is assigned to " + assignmentGroup;
                case RESOLVED -> incident + " is resolved";
                case PENDING -> throw new IllegalStateException();
            };
            audit.record(supportCase.getOrderId(), AuditEvent.ActorType.SYSTEM, SERVICENOW, "follow_incident",
                    AuditEvent.Outcome.SUCCEEDED, summary, null);
        }
        return supportCase;
    }

    public List<SupportCase> unresolved() {
        return cases.findByStatusInOrderByCreatedAt(UNRESOLVED);
    }

    public List<SupportCase> recent() {
        return cases.findTop100ByOrderByCreatedAtDesc();
    }

    public List<SupportCase> forOrder(UUID orderId) {
        return cases.findByOrderIdOrderByCreatedAt(orderId);
    }

    public List<CaseNote> notesOf(UUID caseId) {
        return notes.findByCaseIdOrderByCreatedAt(caseId);
    }

    private SupportCase find(UUID caseId) {
        return cases.findById(caseId)
                .orElseThrow(() -> new GovernanceException(HttpStatus.NOT_FOUND, "Case " + caseId + " does not exist"));
    }

    private static String incidentOf(SupportCase supportCase) {
        return supportCase.getIncidentNumber() == null ? "" : " (" + supportCase.getIncidentNumber() + ")";
    }

    /** Fits ServiceNow's description and work note, however long the details. */
    private static String fit(String details) {
        if (details == null || details.isBlank()) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT, "Say what the problem is");
        }
        return details.length() <= MAX_TEXT_LENGTH ? details : details.substring(0, MAX_TEXT_LENGTH - 1) + "…";
    }
}
