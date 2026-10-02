package io.github.exepex.commerce.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Refund {

    @Id
    private UUID id;

    @Column(name = "payment_id")
    private UUID paymentId;

    private BigDecimal amount;

    private String reason;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Refund() {
        // for JPA
    }

    Refund(UUID paymentId, BigDecimal amount, String reason, String idempotencyKey, String providerReference,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.providerReference = providerReference;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
