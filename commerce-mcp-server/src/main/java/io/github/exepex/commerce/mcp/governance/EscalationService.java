package io.github.exepex.commerce.mcp.governance;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscalationService {

    private static final int MAX_NOTE_LENGTH = 1000;
    private static final List<Escalation.Status> UNRESOLVED = List.of(Escalation.Status.OPEN, Escalation.Status.ASSIGNED);

    private final EscalationRepository escalations;
    private final AuditTrail audit;
    private final JdbcClient jdbc;
    private final Clock clock;

    EscalationService(EscalationRepository escalations, AuditTrail audit, JdbcClient jdbc, Clock clock) {
        this.escalations = escalations;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Hands a problem to a person. An order has at most one unresolved escalation, so it has at most one owner: handing
     * over an order that is already with a person returns that escalation and only records the new summary. The
     * escalation and its audit entry are saved together or not at all.
     */
    @Transactional
    public Escalation escalate(AuditEvent.ActorType raisedByType, String raisedBy, UUID orderId, String summary) {
        if (orderId != null) {
            // Two hand-offs of the same order at once must not each miss the other's escalation.
            jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:orderId, 1))")
                    .param("orderId", orderId.toString())
                    .query((row, number) -> number)
                    .single();
            List<Escalation> unresolved = escalations.findByOrderIdAndStatusIn(orderId, UNRESOLVED);
            if (!unresolved.isEmpty()) {
                audit.record(orderId, raisedByType, raisedBy, "escalate_to_human",
                        AuditEvent.Outcome.SUCCEEDED, "Already with a human; added to the open escalation", summary);
                return unresolved.getFirst();
            }
        }
        Escalation escalation = escalations.save(new Escalation(orderId, raisedBy, summary, Instant.now(clock)));
        audit.record(orderId, raisedByType, raisedBy, "escalate_to_human", AuditEvent.Outcome.SUCCEEDED,
                "Handed over to a human", summary);
        return escalation;
    }

    /** Takes an open escalation: from now on only this person works on it. A second person gets a conflict. */
    @Transactional
    public Escalation assign(UUID escalationId, String person) {
        Escalation escalation = find(escalationId);
        if (escalations.assign(escalationId, person, Instant.now(clock)) == 0) {
            throw new GovernanceException(HttpStatus.CONFLICT, describe(find(escalationId)));
        }
        audit.record(escalation.getOrderId(), AuditEvent.ActorType.HUMAN, person, "assign_escalation",
                AuditEvent.Outcome.SUCCEEDED, "Took the escalation", null);
        return find(escalationId);
    }

    /** Puts an escalation back in the queue for someone else. Only the person it is assigned to can do this. */
    @Transactional
    public Escalation handBack(UUID escalationId, String person, String note) {
        requireNoteFits(note);
        Escalation escalation = find(escalationId);
        if (escalations.handBack(escalationId, person) == 0) {
            throw new GovernanceException(HttpStatus.CONFLICT, describe(escalation));
        }
        audit.record(escalation.getOrderId(), AuditEvent.ActorType.HUMAN, person, "hand_back_escalation",
                AuditEvent.Outcome.SUCCEEDED, "Handed the escalation back to the queue", note);
        return find(escalationId);
    }

    /**
     * Closes an escalation. Only the person it is assigned to can do this, and the resolution and its audit entry are
     * saved together.
     */
    @Transactional
    public Escalation resolve(UUID escalationId, String person, String note) {
        requireNoteFits(note);
        Escalation escalation = find(escalationId);
        if (escalations.resolve(escalationId, person, note, Instant.now(clock)) == 0) {
            throw new GovernanceException(HttpStatus.CONFLICT, describe(escalation));
        }
        audit.record(escalation.getOrderId(), AuditEvent.ActorType.HUMAN, person, "resolve_escalation",
                AuditEvent.Outcome.SUCCEEDED, "Resolved the escalation", note);
        return find(escalationId);
    }

    /**
     * Work on an escalated order, such as retrying its refund, is for the person the escalation is assigned to. An order
     * without an unresolved escalation is not restricted.
     */
    public void ensureWorkedBy(UUID orderId, String person) {
        for (Escalation escalation : escalations.findByOrderIdAndStatusIn(orderId, UNRESOLVED)) {
            if (!person.equals(escalation.getAssignedTo())) {
                throw new GovernanceException(HttpStatus.CONFLICT, describe(escalation));
            }
        }
    }

    /** Once an order is with a person, agents leave its unfinished work to them. */
    public void ensureNotWithHuman(UUID orderId) {
        if (!escalations.findByOrderIdAndStatusIn(orderId, UNRESOLVED).isEmpty()) {
            throw new GovernanceException(HttpStatus.CONFLICT, "This order was handed to a human, who will finish it. "
                    + "Do not retry; tell the customer a person is looking into it.");
        }
    }

    public List<Escalation> withStatus(Escalation.Status status) {
        return escalations.findByStatusOrderByCreatedAt(status);
    }

    public List<Escalation> recent() {
        return escalations.findTop100ByOrderByCreatedAtDesc();
    }

    private Escalation find(UUID escalationId) {
        return escalations.findById(escalationId).orElseThrow(
                () -> new GovernanceException(HttpStatus.NOT_FOUND, "Escalation " + escalationId + " does not exist"));
    }

    private static void requireNoteFits(String note) {
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "A note can be at most " + MAX_NOTE_LENGTH + " characters");
        }
    }

    /** Why someone cannot act on the escalation now, in words for the operations console. */
    private static String describe(Escalation escalation) {
        return switch (escalation.getStatus()) {
            case OPEN -> "The escalation is not assigned to you; assign it to yourself first";
            case ASSIGNED -> "The escalation is assigned to " + escalation.getAssignedTo();
            case RESOLVED -> "The escalation was already resolved by " + escalation.getResolvedBy();
        };
    }
}
