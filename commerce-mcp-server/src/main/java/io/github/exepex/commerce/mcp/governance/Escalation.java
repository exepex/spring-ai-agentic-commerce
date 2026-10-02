package io.github.exepex.commerce.mcp.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/** Work an agent handed to a human because it could not, or should not, finish it on its own. */
@Entity
public class Escalation {

    public enum Status {
        OPEN,
        RESOLVED
    }

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "raised_by")
    private String raisedBy;

    private String summary;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Escalation() {
        // for JPA
    }

    Escalation(UUID orderId, String raisedBy, String summary, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.raisedBy = raisedBy;
        this.summary = summary;
        this.status = Status.OPEN;
        this.createdAt = createdAt;
    }

    void resolve(String by, String note, Instant now) {
        status = Status.RESOLVED;
        resolvedBy = by;
        resolutionNote = note;
        resolvedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getRaisedBy() {
        return raisedBy;
    }

    public String getSummary() {
        return summary;
    }

    public Status getStatus() {
        return status;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
