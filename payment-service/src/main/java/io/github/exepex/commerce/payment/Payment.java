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

/** The card payment for one order, and how much of it has been refunded. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    enum Status {
        SUCCEEDED,
        DECLINED
    }

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_email")
    private String customerEmail;

    private BigDecimal amount;

    @Column(name = "refunded_amount")
    private BigDecimal refundedAmount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String provider;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "created_at")
    private Instant createdAt;

    Payment(UUID orderId, String customerEmail, BigDecimal amount, String currency, String provider,
            PaymentGateway.ChargeResult charge, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.amount = amount;
        this.refundedAmount = BigDecimal.ZERO;
        this.currency = currency;
        this.status = charge.succeeded() ? Status.SUCCEEDED : Status.DECLINED;
        this.provider = provider;
        this.providerReference = charge.reference();
        this.failureMessage = charge.failureMessage();
        this.createdAt = createdAt;
    }

    public BigDecimal refundable() {
        return status == Status.SUCCEEDED ? amount.subtract(refundedAmount) : BigDecimal.ZERO;
    }

    /** A repeated charge must be for the same customer, amount and currency as the first one. */
    boolean isSameChargeAs(String otherCustomerEmail, BigDecimal otherAmount, String otherCurrency) {
        return amount.compareTo(otherAmount) == 0 && currency.equals(otherCurrency)
                && customerEmail.equalsIgnoreCase(otherCustomerEmail);
    }

    void recordRefund(BigDecimal refundAmount) {
        refundedAmount = refundedAmount.add(refundAmount);
    }

    /** A refund failed at the processor after it was recorded: its amount was never returned. */
    void reverseRefund(BigDecimal failedAmount) {
        refundedAmount = refundedAmount.subtract(failedAmount);
    }
}
