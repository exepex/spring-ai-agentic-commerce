package io.github.exepex.commerce.mcp.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One thing that happened: who or what did it, to which order, how it turned out, and why. */
@Entity
@Table(name = "audit_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    public enum ActorType {
        AGENT,
        HUMAN,
        SYSTEM
    }

    public enum Outcome {
        SUCCEEDED,
        FAILED,
        DENIED,
        PENDING_APPROVAL,
        REJECTED
    }

    @Id
    @Getter
    private UUID id;

    @Column(name = "occurred_at")
    @Getter
    private Instant occurredAt;

    @Column(name = "order_id")
    @Getter
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type")
    @Getter
    private ActorType actorType;

    @Getter
    private String actor;

    @Getter
    private String action;

    @Enumerated(EnumType.STRING)
    @Getter
    private Outcome outcome;

    @Getter
    private String summary;

    @Getter
    private String details;

    @Column(name = "trace_id")
    @Getter
    private String traceId;

    /** The id the announcing service gave a system event, so a redelivered event is recorded only once. */
    @Column(name = "source_event_id")
    private UUID sourceEventId;

    AuditEvent(Instant occurredAt, UUID orderId, ActorType actorType, String actor, String action, Outcome outcome,
            String summary, String details, String traceId, UUID sourceEventId) {
        this.id = UUID.randomUUID();
        this.occurredAt = occurredAt;
        this.orderId = orderId;
        this.actorType = actorType;
        this.actor = actor;
        this.action = action;
        this.outcome = outcome;
        this.summary = summary;
        this.details = details;
        this.traceId = traceId;
        this.sourceEventId = sourceEventId;
    }
}
