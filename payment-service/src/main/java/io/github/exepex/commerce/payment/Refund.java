package io.github.exepex.commerce.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One refund of a payment, and where it stands at the card processor. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(name = "succeeded_at")
    private Instant succeededAt;

    @Column(name = "created_at")
    private Instant createdAt;

    /** When the refund check last asked the processor about it. */
    @Column(name = "checked_at")
    private Instant checkedAt;

    Refund(UUID paymentId, BigDecimal amount, String reason, String idempotencyKey,
            PaymentGateway.RefundResult result, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.providerReference = result.reference();
        this.status = result.status();
        this.succeededAt = result.status() == PaymentGateway.RefundStatus.SUCCEEDED ? createdAt : null;
        this.createdAt = createdAt;
    }

    /** A repeated refund must be of the same payment and for the same amount as the first one. */
    boolean isSameRefundAs(UUID otherPaymentId, BigDecimal otherAmount) {
        return paymentId.equals(otherPaymentId) && amount.compareTo(otherAmount) == 0;
    }

    void updateStatus(PaymentGateway.RefundStatus latest, Instant now) {
        if (latest == PaymentGateway.RefundStatus.SUCCEEDED && status != PaymentGateway.RefundStatus.SUCCEEDED) {
            succeededAt = now;
        }
        status = latest;
    }
}
