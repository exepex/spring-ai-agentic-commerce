package io.github.exepex.commerce.mcp.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A refund an agent asked for, and what became of it. */
@Entity
@Table(name = "refund_request")
public class RefundRequest {

    public enum Status {
        /** Above the approval threshold: waiting for a human. */
        PENDING_APPROVAL,
        /** The payment service refunded it. */
        EXECUTED,
        /** The payment service could not be reached; retrying with the same idempotency key is safe. */
        FAILED,
        /** A human turned it down. */
        REJECTED
    }

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(name = "order_id")
    private UUID orderId;

    private BigDecimal amount;

    private String currency;

    private String reason;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "requested_by")
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "provider_reference")
    private String providerReference;

    private String failure;

    /** The card processor failed the refund after accepting it; no later answer from the payment service undoes that. */
    @Column(name = "failed_at_processor")
    private boolean failedAtProcessor;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected RefundRequest() {
        // for JPA
    }

    RefundRequest(UUID orderId, BigDecimal amount, String currency, String reason, String idempotencyKey,
            String requestedBy, Status status, Instant now) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.requestedBy = requestedBy;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    boolean matches(UUID otherOrderId, BigDecimal otherAmount) {
        return orderId.equals(otherOrderId) && amount.compareTo(otherAmount) == 0;
    }

    void markExecuted(String reference, Instant now) {
        status = Status.EXECUTED;
        failedAtProcessor = false;
        providerReference = reference;
        failure = null;
        updatedAt = now;
    }

    void markFailed(String message, Instant now) {
        status = Status.FAILED;
        failure = message;
        updatedAt = now;
    }

    void recordDecision(String by, String note, Instant now) {
        decidedBy = by;
        decisionNote = note;
        decidedAt = now;
        updatedAt = now;
    }

    void reject(String by, String note, Instant now) {
        recordDecision(by, note, now);
        status = Status.REJECTED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReason() {
        return reason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Status getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getFailure() {
        return failure;
    }

    public boolean isFailedAtProcessor() {
        return failedAtProcessor;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
