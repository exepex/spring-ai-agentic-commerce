package io.github.exepex.commerce.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    private PaymentGateway.RefundStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Refund() {
        // for JPA
    }

    Refund(UUID paymentId, BigDecimal amount, String reason, String idempotencyKey,
            PaymentGateway.RefundResult result, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.providerReference = result.reference();
        this.status = result.status();
        this.createdAt = createdAt;
    }

    void updateStatus(PaymentGateway.RefundStatus latest) {
        status = latest;
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

    public PaymentGateway.RefundStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
