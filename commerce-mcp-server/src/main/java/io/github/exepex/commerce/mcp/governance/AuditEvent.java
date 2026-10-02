package io.github.exepex.commerce.mcp.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One thing that happened: who or what did it, to which order, how it turned out, and why. */
@Entity
@Table(name = "audit_event")
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
    private UUID id;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type")
    private ActorType actorType;

    private String actor;

    private String action;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    private String summary;

    private String details;

    @Column(name = "trace_id")
    private String traceId;

    /** The id the announcing service gave a system event, so a redelivered event is recorded only once. */
    @Column(name = "source_event_id")
    private UUID sourceEventId;

    protected AuditEvent() {
        // for JPA
    }

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

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetails() {
        return details;
    }

    public String getTraceId() {
        return traceId;
    }
}
