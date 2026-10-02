package io.github.exepex.commerce.mcp.governance;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EscalationService {

    private static final int MAX_NOTE_LENGTH = 1000;

    private final EscalationRepository escalations;
    private final AuditTrail audit;
    private final Clock clock;

    EscalationService(EscalationRepository escalations, AuditTrail audit, Clock clock) {
        this.escalations = escalations;
        this.audit = audit;
        this.clock = clock;
    }

    /** The escalation and its audit entry are saved together or not at all. */
    @Transactional
    public Escalation escalate(String raisedBy, UUID orderId, String summary) {
        Escalation escalation = escalations.save(new Escalation(orderId, raisedBy, summary, Instant.now(clock)));
        audit.record(orderId, AuditEvent.ActorType.AGENT, raisedBy, "escalate_to_human", AuditEvent.Outcome.SUCCEEDED,
                "Handed over to a human", summary);
        return escalation;
    }

    /** The resolution and its audit entry are saved together, so the audit trail never shows one that did not happen. */
    @Transactional
    public Escalation resolve(UUID escalationId, String resolvedBy, String note) {
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "A resolution note can be at most " + MAX_NOTE_LENGTH + " characters");
        }
        Escalation escalation = escalations.findById(escalationId)
                .orElseThrow(() -> new GovernanceException(HttpStatus.NOT_FOUND, "Escalation " + escalationId + " does not exist"));
        if (escalation.getStatus() == Escalation.Status.OPEN) {
            escalation.resolve(resolvedBy, note, Instant.now(clock));
            audit.record(escalation.getOrderId(), AuditEvent.ActorType.HUMAN, resolvedBy, "resolve_escalation",
                    AuditEvent.Outcome.SUCCEEDED, "Resolved the escalation", note);
        }
        return escalations.save(escalation);
    }

    public List<Escalation> withStatus(Escalation.Status status) {
        return escalations.findByStatusOrderByCreatedAt(status);
    }

    public List<Escalation> recent() {
        return escalations.findTop100ByOrderByCreatedAtDesc();
    }
}
