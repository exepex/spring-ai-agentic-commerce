package io.github.exepex.commerce.mcp.cases;

import io.github.exepex.commerce.mcp.cases.dto.Outgoing;
import io.github.exepex.commerce.mcp.constants.Actors;
import io.github.exepex.commerce.mcp.constants.AuditActions;
import io.github.exepex.commerce.mcp.constants.CaseWording;
import io.github.exepex.commerce.mcp.constants.ConfigKeys;
import io.github.exepex.commerce.mcp.exception.CaseNotFoundException;
import io.github.exepex.commerce.mcp.exception.CaseNoteNotFoundException;
import io.github.exepex.commerce.mcp.exception.NotTheCaseIncidentException;
import io.github.exepex.commerce.mcp.exception.OrderHandledByPeopleException;
import io.github.exepex.commerce.mcp.exception.PendingIncidentStateException;
import io.github.exepex.commerce.mcp.exception.ServiceDeskIncidentNotWorkedException;
import io.github.exepex.commerce.mcp.governance.AdvisoryLocks;
import io.github.exepex.commerce.mcp.governance.AuditEvent;
import io.github.exepex.commerce.mcp.governance.AuditTrail;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
@RequiredArgsConstructor
public class CaseService {

    private static final List<SupportCase.Status> UNRESOLVED = List.of(SupportCase.Status.PENDING,
            SupportCase.Status.WITH_AGENT, SupportCase.Status.WITH_TEAM);
    private static final List<SupportCase.Status> IN_SERVICENOW = List.of(SupportCase.Status.WITH_AGENT,
            SupportCase.Status.WITH_TEAM);

    private final SupportCaseRepository cases;
    private final CaseNoteRepository notes;
    private final ServiceDeskCases serviceDesk;
    private final AuditTrail audit;
    private final JdbcClient jdbc;
    private final Clock clock;

    @Value(ConfigKeys.CASE_WORKER)
    private final String worker;

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
        var text = CaseTexts.fit(details);
        var now = Instant.now(clock);
        if (orderId != null) {
            // Two raises of the same problem at once must not each miss the other's case.
            lockProblem(orderId, type);
            var open = cases.findByOrderIdAndTypeAndStatusNotAndReopenedBesideOpenCaseFalse(orderId, type,
                    SupportCase.Status.RESOLVED);
            if (open.isEmpty()) {
                // The case a reopened one stood beside is resolved: the reopened one takes the problem over.
                open = cases.findFirstByOrderIdAndTypeAndStatusNotAndReopenedBesideOpenCaseTrueOrderByCreatedAtDesc(
                        orderId, type, SupportCase.Status.RESOLVED);
                open.ifPresent(SupportCase::becomeTheOpenCase);
            }
            if (open.isPresent()) {
                notes.save(new CaseNote(open.get().getId(), text, now));
                audit.record(orderId, raisedByType, raisedBy, AuditActions.RAISE_CASE, AuditEvent.Outcome.SUCCEEDED,
                        CaseWording.ADDED_TO_OPEN_CASE.formatted(type, CaseTexts.incidentOf(open.get())), text);
                return open.get();
            }
        }
        var opened = cases.save(new SupportCase(orderId, type, text, raisedBy, now));
        audit.record(orderId, raisedByType, raisedBy, AuditActions.RAISE_CASE, AuditEvent.Outcome.SUCCEEDED,
                CaseWording.OPENED_CASE.formatted(type), text);
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
        var open = cases.findByOrderIdAndStatusIn(orderId, UNRESOLVED).stream()
                .filter(supportCase -> blocksPayment(supportCase, agentId, incidentNumber))
                .findFirst();
        if (open.isEmpty()) {
            return;
        }
        throw new OrderHandledByPeopleException(CaseTexts.holderOf(open.get()));
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
        var unsentByCase = new LinkedHashMap<UUID, List<CaseNote>>();
        for (var note : notes.findBySentAtIsNullOrderByCreatedAt()) {
            unsentByCase.computeIfAbsent(note.getCaseId(), caseId -> new ArrayList<>()).add(note);
        }
        var outgoing = new LinkedHashMap<UUID, SupportCase>();
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
        var supportCase = find(caseId);
        if (supportCase.linkIncident(number, url, Instant.now(clock))) {
            audit.record(supportCase.getOrderId(), AuditEvent.ActorType.SYSTEM, Actors.SERVICENOW,
                    AuditActions.OPEN_INCIDENT, AuditEvent.Outcome.SUCCEEDED,
                    CaseWording.OPENED_INCIDENT.formatted(number, supportCase.getType()), null);
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
            throw new ServiceDeskIncidentNotWorkedException();
        }
        return serviceDesk.record(orderId, number, url, shortDescription, status, assignmentGroup);
    }

    @Transactional
    public void markNoteSent(UUID caseId, UUID noteId) {
        notes.findById(noteId)
                .filter(note -> note.getCaseId().equals(caseId))
                .orElseThrow(() -> new CaseNoteNotFoundException(caseId, noteId))
                .markSent(Instant.now(clock));
    }

    /**
     * Cases whose incident the poller reads back: those whose incident is open in ServiceNow, and those whose incident
     * is resolved but may still be reopened, until ServiceNow closes or cancels it.
     */
    public List<SupportCase> inServiceNow() {
        var inServiceNow = new ArrayList<SupportCase>(cases.findByStatusInOrderByCreatedAt(IN_SERVICENOW));
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
            throw new PendingIncidentStateException();
        }
        var supportCase = find(caseId);
        if (!number.equals(supportCase.getIncidentNumber())) {
            throw new NotTheCaseIncidentException(number, caseId);
        }
        if (supportCase.getOrderId() != null) {
            lockProblem(supportCase.getOrderId(), supportCase.getType());
        }
        var now = Instant.now(clock);
        if (supportCase.getType() == CaseType.SERVICE_DESK
                && serviceDesk.followOrder(supportCase, number, status, assignmentGroup, incidentFinal, incidentOrderId,
                        now)) {
            return supportCase;
        }
        var reopened = supportCase.getStatus() == SupportCase.Status.RESOLVED
                && status != SupportCase.Status.RESOLVED;
        // Looked up before the case changes, so that it does not find itself.
        var open = reopened ? openCaseOfTheSameProblem(supportCase) : Optional.<SupportCase>empty();
        if (supportCase.followIncident(status, assignmentGroup, incidentFinal, open.isPresent(), Instant.now(clock))) {
            var summary = CaseTexts.followed(supportCase.getIncidentNumber(), reopened, status, assignmentGroup);
            var details = open.map(other -> CaseWording.OPEN_CASE_TAKES_PROBLEM.formatted(other.getType(),
                    CaseTexts.incidentOf(other))).orElse(null);
            audit.record(supportCase.getOrderId(), AuditEvent.ActorType.SYSTEM, Actors.SERVICENOW,
                    AuditActions.FOLLOW_INCIDENT, AuditEvent.Outcome.SUCCEEDED, summary, details);
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
        var unsent = notes.findByCaseIdAndSentAtIsNullOrderByCreatedAt(resolved.getId());
        if (unsent.isEmpty()) {
            return;
        }
        var reopened = openOrAddTo(resolved.getType(), resolved.getOrderId(),
                CaseWording.RAISED_AGAIN.formatted(resolved.getIncidentNumber()), AuditEvent.ActorType.SYSTEM,
                Actors.SERVICENOW);
        unsent.forEach(note -> note.moveTo(reopened.getId()));
    }

    /** Serializes everything that opens, adds to or resolves the order's case of this type. */
    private void lockProblem(UUID orderId, CaseType type) {
        AdvisoryLocks.lock(jdbc, CaseWording.PROBLEM_LOCK_KEY.formatted(orderId, type), 2);
    }

    /** The oldest unresolved cases, for the operations console: a page bounded however many are open. */
    public List<SupportCase> unresolved() {
        return cases.findTop500ByStatusInOrderByCreatedAt(UNRESOLVED);
    }

    public List<SupportCase> recent() {
        return cases.findTop100ByOrderByCreatedAtDesc();
    }

    public List<SupportCase> forOrder(UUID orderId) {
        return cases.findByOrderIdOrderByCreatedAt(orderId);
    }

    private SupportCase find(UUID caseId) {
        return cases.findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
    }
}
