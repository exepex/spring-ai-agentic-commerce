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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens a case for every problem that needs handling, and keeps it in step with its ServiceNow incident. Code opens
 * cases, never a model's judgement: a stock-out, a delivery that failed, a lost parcel, a refund that failed at the
 * card processor, and every hand-off an agent asks for. An incident the service desk raised about an order is recorded
 * as a case too.
 *
 * <p>An order has at most one unresolved case of each type, so the same problem raised again adds a note to the open
 * case instead of opening a second one. The ServiceNow MCP server's poller carries cases and notes to ServiceNow and
 * reports back who has each incident.
 */
@Service
public class CaseService {

    /** The actor recorded for what ServiceNow did, as reported by its poller. */
    static final String SERVICENOW = "servicenow";
    private static final String RAISE_CASE = "raise_case";
    private static final String FOLLOW_INCIDENT = "follow_incident";

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
    private final String worker;

    CaseService(SupportCaseRepository cases, CaseNoteRepository notes, AuditTrail audit, JdbcClient jdbc, Clock clock,
            @Value("${commerce.cases.worker}") String worker) {
        this.cases = cases;
        this.notes = notes;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.worker = worker;
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
        if (isFirstDelivery(sourceEventId, orderId)) {
            openOrAddTo(type, orderId, details, AuditEvent.ActorType.SYSTEM, service);
        }
    }

    /**
     * Whether this is the first time the event is handled for the order. Kafka can deliver an event again; only the
     * first delivery may raise a case. Runs in the caller's transaction, which raises the case too, so a delivery that
     * fails raises nothing and counts as never handled.
     */
    public boolean isFirstDelivery(UUID sourceEventId, UUID orderId) {
        return jdbc.sql("insert into governance.case_event (source_event_id, order_id) values (:eventId, :orderId) "
                        + "on conflict do nothing")
                .param("eventId", sourceEventId)
                .param("orderId", orderId)
                .update() == 1;
    }

    /** Opens the case or adds to the open one, within the caller's transaction. */
    private SupportCase openOrAddTo(CaseType type, UUID orderId, String details, AuditEvent.ActorType raisedByType,
            String raisedBy) {
        String text = fit(details);
        Instant now = Instant.now(clock);
        if (orderId != null) {
            // Two raises of the same problem at once must not each miss the other's case.
            lockProblem(orderId, type);
            Optional<SupportCase> open = cases.findByOrderIdAndTypeAndStatusNotAndReopenedBesideOpenCaseFalse(orderId,
                    type, SupportCase.Status.RESOLVED);
            if (open.isEmpty()) {
                // The case a reopened one stood beside is resolved: the reopened one takes the problem over.
                open = cases.findFirstByOrderIdAndTypeAndStatusNotAndReopenedBesideOpenCaseTrueOrderByCreatedAtDesc(
                        orderId, type, SupportCase.Status.RESOLVED);
                open.ifPresent(SupportCase::becomeTheOpenCase);
            }
            if (open.isPresent()) {
                notes.save(new CaseNote(open.get().getId(), text, now));
                audit.record(orderId, raisedByType, raisedBy, RAISE_CASE, AuditEvent.Outcome.SUCCEEDED,
                        "Added to the open " + type + " case" + incidentOf(open.get()), text);
                return open.get();
            }
        }
        SupportCase opened = cases.save(new SupportCase(orderId, type, text, raisedBy, now));
        audit.record(orderId, raisedByType, raisedBy, RAISE_CASE, AuditEvent.Outcome.SUCCEEDED,
                "Opened a " + type + " case; it goes to ServiceNow as an incident for the incident agent", text);
        return opened;
    }

    /**
     * Whether the agent may pay money back on the order now. While any case of the order is open, only the agent that
     * works cases may: the others cannot see who has its incident at this moment. That agent may pay only for the
     * incident it is working ({@code incidentNumber}, set by agent-service from the run, never by the model), and only
     * while each other open case is still on its way to ServiceNow: a person may have taken any other incident since
     * ServiceNow was last read, and while a team has one, they may be paying the customer back another way.
     */
    public void ensureAgentMayPay(UUID orderId, String agentId, String incidentNumber) {
        Optional<SupportCase> open = cases.findByOrderIdAndStatusIn(orderId, UNRESOLVED).stream()
                .filter(supportCase -> blocksPayment(supportCase, agentId, incidentNumber))
                .findFirst();
        if (open.isEmpty()) {
            return;
        }
        SupportCase supportCase = open.get();
        String who = supportCase.getStatus() == SupportCase.Status.WITH_TEAM
                ? "with the " + supportCase.getAssignmentGroup() + " team in ServiceNow" + incidentOf(supportCase)
                : "an open " + supportCase.getType() + " case" + incidentOf(supportCase) + " that the support team handles";
        throw new GovernanceException(HttpStatus.CONFLICT, "This order is " + who + ", who will finish it. Do not "
                + "retry or refund it another way; tell the customer a person is looking into it.");
    }

    private boolean blocksPayment(SupportCase supportCase, String agentId, String incidentNumber) {
        if (!worker.equals(agentId) || supportCase.getStatus() == SupportCase.Status.WITH_TEAM) {
            return true;
        }
        if (supportCase.getStatus() == SupportCase.Status.PENDING) {
            // Not in ServiceNow yet, so nobody can have taken it; unless it is meant for people from the start.
            return supportCase.isForPeople();
        }
        return incidentNumber == null || !incidentNumber.strip().equals(supportCase.getIncidentNumber());
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

    /**
     * Records an incident the service desk raised about an order as a case of its own, so agents leave the order's
     * money to whoever works it, as for the shop's own cases; the read-back then keeps it in step until it is resolved.
     * The incident is known by its link, which names the instance and the incident's sys_id: its number alone repeats
     * across instances, such as the simulator and a real one.
     *
     * <p>Recording an incident again changes nothing while it stays about the same order and its case is open. When
     * the service desk corrected the order in the incident, the case moves to that order, and when the incident was
     * reopened after its case was resolved, the case is open again.
     */
    @Transactional
    public SupportCase recordServiceDeskIncident(UUID orderId, String number, String url, String shortDescription,
            SupportCase.Status status, String assignmentGroup) {
        if (status != SupportCase.Status.WITH_AGENT && status != SupportCase.Status.WITH_TEAM) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "A service-desk incident being worked is with the agent or a team");
        }
        Optional<SupportCase> recorded = cases.findByTypeAndIncidentUrl(CaseType.SERVICE_DESK, url);
        if (recorded.isPresent()) {
            followServiceDeskIncident(recorded.get(), number, orderId, status, assignmentGroup);
            return recorded.get();
        }
        SupportCase supportCase = cases.save(SupportCase.forServiceDeskIncident(orderId, number, url, shortDescription,
                status, assignmentGroup, Instant.now(clock)));
        audit.record(orderId, AuditEvent.ActorType.SYSTEM, SERVICENOW, RAISE_CASE, AuditEvent.Outcome.SUCCEEDED,
                "The service desk raised incident " + number + " about this order; agents leave its money to whoever "
                        + "works it", shortDescription);
        return supportCase;
    }

    /**
     * Follows a service-desk incident whose Correlation ID no longer names the case's order: one that names none frees
     * the order, and one that names another order takes the case there.
     *
     * @return whether the incident names another order than the case's, or none, and was followed
     */
    private boolean followOrderOfServiceDeskIncident(SupportCase supportCase, String number, SupportCase.Status status,
            String assignmentGroup, boolean incidentFinal, UUID incidentOrderId, Instant now) {
        if (incidentOrderId == null) {
            // Followed even while resolved, so that the case learns when its incident becomes final.
            boolean wasOpen = supportCase.getStatus() != SupportCase.Status.RESOLVED;
            if (supportCase.followIncident(SupportCase.Status.RESOLVED, assignmentGroup, incidentFinal, false, now)
                    && wasOpen) {
                audit.record(supportCase.getOrderId(), AuditEvent.ActorType.SYSTEM, SERVICENOW, FOLLOW_INCIDENT,
                        AuditEvent.Outcome.SUCCEEDED, number + " no longer names this order, so its case is closed and "
                                + "agents may handle the order's money again", null);
            }
            return true;
        }
        if (!incidentOrderId.equals(supportCase.getOrderId())) {
            followServiceDeskIncident(supportCase, number, incidentOrderId, status, assignmentGroup);
            return true;
        }
        return false;
    }

    /** Moves a service-desk case to the order its incident names now, or opens it again, with entries on the timeline. */
    private void followServiceDeskIncident(SupportCase supportCase, String number, UUID orderId,
            SupportCase.Status status, String assignmentGroup) {
        UUID before = supportCase.getOrderId();
        boolean wasResolved = supportCase.getStatus() == SupportCase.Status.RESOLVED;
        if (supportCase.followServiceDeskIncident(orderId, status, assignmentGroup, Instant.now(clock))) {
            if (!orderId.equals(before)) {
                audit.record(before, AuditEvent.ActorType.SYSTEM, SERVICENOW, FOLLOW_INCIDENT,
                        AuditEvent.Outcome.SUCCEEDED, number + " is now about order " + orderId, null);
            }
            audit.record(orderId, AuditEvent.ActorType.SYSTEM, SERVICENOW, FOLLOW_INCIDENT,
                    AuditEvent.Outcome.SUCCEEDED, describeServiceDeskIncident(number, status, wasResolved), null);
        }
    }

    /** A resolved incident blocks nobody, so only an open one tells agents to leave the order's money alone. */
    private static String describeServiceDeskIncident(String number, SupportCase.Status status, boolean wasResolved) {
        if (status == SupportCase.Status.RESOLVED) {
            return number + " is now about this order and is resolved";
        }
        String change = wasResolved ? " was reopened" : " is now about this order";
        return number + change + "; agents leave its money to whoever works it";
    }

    @Transactional
    public void markNoteSent(UUID caseId, UUID noteId) {
        notes.findById(noteId)
                .filter(note -> note.getCaseId().equals(caseId))
                .orElseThrow(() -> new GovernanceException(HttpStatus.NOT_FOUND,
                        "Case " + caseId + " has no note " + noteId))
                .markSent(Instant.now(clock));
    }

    /**
     * Cases whose incident the poller reads back: those whose incident is open in ServiceNow, and those whose incident
     * is resolved but may still be reopened, until ServiceNow closes or cancels it.
     */
    public List<SupportCase> inServiceNow() {
        List<SupportCase> inServiceNow = new ArrayList<>(cases.findByStatusInOrderByCreatedAt(IN_SERVICENOW));
        inServiceNow.addAll(cases.findByStatusAndIncidentFinalFalseOrderByCreatedAt(SupportCase.Status.RESOLVED));
        return inServiceNow;
    }

    /**
     * Takes over who has the case's incident: the agent, a team, or nobody any more because it is resolved. Recorded
     * on the order's timeline when it changes. Only the case's own incident counts: an incident whose Correlation
     * display field was edited to name another case cannot change that case.
     *
     * <p>Notes that had not reached the incident when it was resolved would never be read there, so they go to a new
     * case of the same problem. This holds the same lock as raising the problem, so a raise either lands before the
     * resolution, and is carried over, or after it, and opens the new case.
     *
     * <p>An incident reopened after it was resolved opens its case again, so agents leave the order's money to whoever
     * has it. When the order has a newer open case of the same problem by then, both are open, and the newer one stays
     * the case a problem raised again goes to.
     *
     * <p>A service-desk incident that names no order any more, because the service desk cleared or changed its
     * Correlation ID, is not about the case's order: the case is resolved, so the order's money is free again. It stays
     * so until the incident names an order again, which the poller records like a new one. One that names another order
     * moves its case there.
     *
     * @param incidentFinal whether a resolved incident is closed or cancelled, so it can no longer be reopened
     * @param incidentOrderId the order the incident names now, if any
     */
    @Transactional
    public SupportCase followIncident(UUID caseId, String number, SupportCase.Status status, String assignmentGroup,
            boolean incidentFinal, UUID incidentOrderId) {
        if (status == SupportCase.Status.PENDING) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT, "An incident in ServiceNow is not pending");
        }
        SupportCase supportCase = find(caseId);
        if (!number.equals(supportCase.getIncidentNumber())) {
            throw new GovernanceException(HttpStatus.CONFLICT, "Incident " + number + " is not the incident of case "
                    + caseId);
        }
        if (supportCase.getOrderId() != null) {
            lockProblem(supportCase.getOrderId(), supportCase.getType());
        }
        Instant now = Instant.now(clock);
        if (supportCase.getType() == CaseType.SERVICE_DESK
                && followOrderOfServiceDeskIncident(supportCase, number, status, assignmentGroup, incidentFinal,
                        incidentOrderId, now)) {
            return supportCase;
        }
        boolean reopened = supportCase.getStatus() == SupportCase.Status.RESOLVED
                && status != SupportCase.Status.RESOLVED;
        // Looked up before the case changes, so that it does not find itself.
        Optional<SupportCase> open = reopened ? openCaseOfTheSameProblem(supportCase) : Optional.empty();
        if (supportCase.followIncident(status, assignmentGroup, incidentFinal, open.isPresent(), Instant.now(clock))) {
            String incident = supportCase.getIncidentNumber() + (reopened ? " was reopened and" : "");
            String summary = switch (status) {
                case WITH_AGENT -> incident + " is with the incident agent";
                case WITH_TEAM -> incident + " is assigned to " + assignmentGroup;
                case RESOLVED -> incident + " is resolved";
                case PENDING -> throw new IllegalStateException();
            };
            String details = open.map(other -> "The order's open " + other.getType() + " case" + incidentOf(other)
                    + " still takes what is raised again.").orElse(null);
            audit.record(supportCase.getOrderId(), AuditEvent.ActorType.SYSTEM, SERVICENOW, FOLLOW_INCIDENT,
                    AuditEvent.Outcome.SUCCEEDED, summary, details);
            if (status == SupportCase.Status.RESOLVED) {
                carryOverUnsentNotes(supportCase);
            }
        }
        return supportCase;
    }

    /**
     * The order's open case of the same problem, the one a problem raised again goes to, if it has one; a service-desk
     * case shares its problem with none.
     */
    private Optional<SupportCase> openCaseOfTheSameProblem(SupportCase supportCase) {
        if (supportCase.getOrderId() == null || supportCase.getType() == CaseType.SERVICE_DESK) {
            return Optional.empty();
        }
        return cases.findByOrderIdAndTypeAndStatusNotAndReopenedBesideOpenCaseFalse(supportCase.getOrderId(),
                supportCase.getType(), SupportCase.Status.RESOLVED);
    }

    /**
     * Opens a new case of the same problem for the notes a resolved incident never got. The notes move to it whole, so
     * they reach its incident as work notes, however long they are.
     */
    private void carryOverUnsentNotes(SupportCase resolved) {
        List<CaseNote> unsent = notes.findByCaseIdAndSentAtIsNullOrderByCreatedAt(resolved.getId());
        if (unsent.isEmpty()) {
            return;
        }
        SupportCase reopened = openOrAddTo(resolved.getType(), resolved.getOrderId(), "Raised again after "
                + resolved.getIncidentNumber() + " was resolved; what was raised follows as work notes.",
                AuditEvent.ActorType.SYSTEM, SERVICENOW);
        unsent.forEach(note -> note.moveTo(reopened.getId()));
    }

    /** Serializes everything that opens, adds to or resolves the order's case of this type. */
    private void lockProblem(UUID orderId, CaseType type) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 2))")
                .param("key", orderId + "/" + type)
                .query((row, number) -> number)
                .single();
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
